package com.hznu.campusragbackend.evaluation;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.model.ChunkMetadata;
import com.hznu.campusragbackend.model.Document;
import com.hznu.campusragbackend.model.DocumentChunk;
import com.hznu.campusragbackend.rag.retrieval.RetrievalResult;
import com.hznu.campusragbackend.rag.retrieval.RetrievalService;
import com.hznu.campusragbackend.repository.DocumentChunkRepository;
import com.hznu.campusragbackend.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@Profile("!prod")
public class EvaluationService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final RetrievalService retrievalService;
    private final QuestionGenerator questionGenerator;
    private final File testSetFile;

    private List<EvalSample> testSet = new ArrayList<>();

    record EvalSample(String question, Long documentId, String documentTitle) {}

    public EvaluationService(DocumentRepository documentRepository,
                             DocumentChunkRepository chunkRepository,
                             RetrievalService retrievalService,
                             QuestionGenerator questionGenerator,
                             @Value("${rag.eval.dir:./experiments/eval}") String evalDir) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.retrievalService = retrievalService;
        this.questionGenerator = questionGenerator;
        new File(evalDir).mkdirs();
        this.testSetFile = new File(evalDir, "eval-testset.json");
        loadTestSet();
    }

    /** 对比纯向量、纯ES、混合检索三组结果 */
    public Map<String, Object> compare(String query) {
        int topK = 5;
        List<RetrievalResult> vectorOnly = retrievalService.retrieveVectorOnly(query, topK);
        List<RetrievalResult> esOnly = retrievalService.retrieveEsOnly(query, topK);
        List<RetrievalResult> hybrid = retrievalService.retrieve(query, topK);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("vectorOnly", formatResults(vectorOnly));
        result.put("esOnly", formatResults(esOnly));
        result.put("hybrid", formatResults(hybrid));
        return result;
    }

    private List<Map<String, Object>> formatResults(List<RetrievalResult> results) {
        List<Map<String, Object>> list = new ArrayList<>();
        int rank = 0;
        for (RetrievalResult r : results) {
            rank++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank);
            item.put("chunkId", r.getChunk().getId());
            ChunkMetadata meta = ChunkMetadata.fromJson(r.getChunk().getMetadata());
            item.put("documentTitle", meta.documentTitle());
            String content = r.getChunk().getContent();
            item.put("preview", content.length() > 80 ? content.substring(0, 80) + "..." : content);
            item.put("score", String.format("%.4f", r.getSimilarityScore()));
            list.add(item);
        }
        return list;
    }

    /** 从已有文档自动生成评测问题集，写入 eval-testset.json */
    public Map<String, Object> generateTestSet() {
        List<Document> documents = documentRepository.selectList(null);
        if (documents.isEmpty()) return Map.of("message", "没有文档", "count", 0);

        testSet.clear();
        AtomicInteger generated = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        for (Document doc : documents) {
            try {
                List<DocumentChunk> chunks = chunkRepository.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                                .eq(DocumentChunk::getDocumentId, doc.getId())
                                .orderByAsc(DocumentChunk::getChunkIndex)
                );
                if (chunks.isEmpty()) continue;

                int sampleCount = Math.min(3, chunks.size());
                List<DocumentChunk> samples = pickDiverseSamples(chunks, sampleCount);

                for (DocumentChunk chunk : samples) {
                    Optional<String> question = generateQuestion(chunk.getContent(), doc.getTitle());
                    if (question.isPresent()) {
                        testSet.add(new EvalSample(question.get(), doc.getId(), doc.getTitle()));
                        generated.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                    // 避免触发 DashScope 限流，每次调用间隔 500ms
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            } catch (Exception e) {
                log.warn("生成问题失败: documentId={}", doc.getId(), e);
            }
        }

        saveTestSet();
        log.info("评测集生成完成: {}条问题, 跳过{}条, 已写入 {}", generated.get(), skipped.get(), testSetFile);
        return Map.of("message", "评测集生成完成", "file", testSetFile.getPath(),
                "totalQuestions", testSet.size(), "documents", documents.size(),
                "skipped", skipped.get());
    }

    /** 运行评测，对比向量/ES/混合三种检索方法，结果写入 eval-results.json */
    public Map<String, Object> evaluate(int topK) {
        if (testSet.isEmpty()) {
            return Map.of("message", "评测集为空，请先调用 POST /admin/eval/generate 生成");
        }

        List<Map<String, Object>> queryDetails = new ArrayList<>();
        Accumulator vecAcc = new Accumulator();
        Accumulator esAcc = new Accumulator();
        Accumulator hybAcc = new Accumulator();

        for (EvalSample sample : testSet) {
            List<Long> vecDocIds = docIdsInOrder(retrievalService.retrieveVectorOnly(sample.question(), topK));
            List<Long> esDocIds = docIdsInOrder(retrievalService.retrieveEsOnly(sample.question(), topK));
            List<Long> hybDocIds = docIdsInOrder(retrievalService.retrieve(sample.question(), topK));

            vecAcc.record(sample.documentId(), vecDocIds);
            esAcc.record(sample.documentId(), esDocIds);
            hybAcc.record(sample.documentId(), hybDocIds);

            // 记录每条问题的详细命中情况
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("question", sample.question());
            detail.put("documentTitle", sample.documentTitle());
            detail.put("vectorHit", hitRank(sample.documentId(), vecDocIds));
            detail.put("esHit", hitRank(sample.documentId(), esDocIds));
            detail.put("hybridHit", hitRank(sample.documentId(), hybDocIds));
            queryDetails.add(detail);
        }

        int total = testSet.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("totalQueries", total);
        result.put("topK", topK);
        result.put("vectorOnly", toMap(vecAcc, total));
        result.put("esOnly", toMap(esAcc, total));
        result.put("hybrid", toMap(hybAcc, total));

        // 写汇总文件
        File resultFile = new File(testSetFile.getParent(), "eval-results.json");
        FileWriter.create(resultFile).write(JSONUtil.toJsonPrettyStr(result));
        log.info("评测结果已写入: {}", resultFile);

        // 写逐条详情
        File detailFile = new File(testSetFile.getParent(), "eval-queries.json");
        JSONArray detailArr = new JSONArray();
        for (Map<String, Object> d : queryDetails) {
            detailArr.add(new JSONObject(d));
        }
        FileWriter.create(detailFile).write(JSONUtil.toJsonPrettyStr(detailArr.toString()));
        log.info("逐条评测详情已写入: {}", detailFile);

        // 控制台对比摘要
        log.info("========== 检索评测 (topK={}) ==========", topK);
        for (String label : List.of("vectorOnly", "esOnly", "hybrid")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) result.get(label);
            log.info("  {}: Recall@3={}, Recall@5={}, MRR={}",
                    label, m.get("recall@3"), m.get("recall@5"), m.get("mrr"));
        }

        result.put("perQueryDetailFile", detailFile.getPath());
        return result;
    }

    /** 查看当前评测集 */
    public List<Map<String, Object>> getTestSet() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (EvalSample s : testSet) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", s.question());
            m.put("documentId", s.documentId());
            m.put("documentTitle", s.documentTitle());
            list.add(m);
        }
        return list;
    }

    // ────────── 文件存储 ──────────

    private void saveTestSet() {
        JSONArray arr = new JSONArray();
        for (EvalSample s : testSet) {
            JSONObject obj = new JSONObject();
            obj.set("question", s.question());
            obj.set("documentId", s.documentId());
            obj.set("documentTitle", s.documentTitle());
            arr.add(obj);
        }
        FileWriter.create(testSetFile).write(JSONUtil.toJsonPrettyStr(arr.toString()));
    }

    private void loadTestSet() {
        if (!testSetFile.exists()) return;
        String content = FileReader.create(testSetFile).readString();
        JSONArray arr = JSONUtil.parseArray(content);
        testSet.clear();
        for (Object o : arr) {
            JSONObject obj = (JSONObject) o;
            testSet.add(new EvalSample(
                    obj.getStr("question"),
                    obj.getLong("documentId"),
                    obj.getStr("documentTitle")
            ));
        }
        log.info("已加载评测集: {}条, 来源: {}", testSet.size(), testSetFile);
    }

    // ────────── 内部方法 ──────────

    private List<DocumentChunk> pickDiverseSamples(List<DocumentChunk> chunks, int count) {
        if (chunks.size() <= count) return new ArrayList<>(chunks);
        List<DocumentChunk> result = new ArrayList<>();
        int step = chunks.size() / count;
        for (int i = 0; i < count; i++) {
            result.add(chunks.get(Math.min(i * step, chunks.size() - 1)));
        }
        return result;
    }

    private Optional<String> generateQuestion(String chunkContent, String docTitle) {
        try {
            String clip = chunkContent.length() > 800 ? chunkContent.substring(0, 800) : chunkContent;
            String answer = questionGenerator.generate(docTitle, clip);
            String q = answer.trim()
                    .replaceAll("^[\"「『]|[\"」』]$", "")
                    .replaceAll("^问题[：:]\\s*", "")
                    .strip();
            if (q.length() >= 5 && q.length() <= 200) {
                return Optional.of(q);
            }
            log.warn("生成的问题不符合长度要求: {}", q);
        } catch (Exception e) {
            log.warn("LLM生成问题失败: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private List<Long> docIdsInOrder(List<RetrievalResult> results) {
        return results.stream()
                .map(r -> r.getChunk().getDocumentId())
                .distinct()
                .toList();
    }

    /** 返回命中排名 (1-based)，未命中返回 -1 */
    private int hitRank(Long targetDocId, List<Long> retrievedDocIds) {
        for (int i = 0; i < retrievedDocIds.size(); i++) {
            if (retrievedDocIds.get(i).equals(targetDocId)) return i + 1;
        }
        return -1;
    }

    private Map<String, Object> toMap(Accumulator acc, int total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recall@3", String.format("%.2f%%", acc.recallAt3 * 100.0 / total));
        m.put("recall@5", String.format("%.2f%%", acc.recallAt5 * 100.0 / total));
        m.put("mrr", String.format("%.4f", acc.sumRR / total));
        m.put("totalQueries", total);
        return m;
    }

    private static class Accumulator {
        double sumRR;
        int recallAt3;
        int recallAt5;

        void record(Long targetDocId, List<Long> retrievedDocIds) {
            for (int i = 0; i < retrievedDocIds.size(); i++) {
                if (retrievedDocIds.get(i).equals(targetDocId)) {
                    sumRR += 1.0 / (i + 1);
                    if (i < 3) recallAt3++;
                    if (i < 5) recallAt5++;
                    break;
                }
            }
        }
    }
}
