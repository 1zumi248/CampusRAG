<script setup lang="ts">
import type { ChatResponse } from '@/api/chat'
import { marked } from 'marked'
import hljs from 'highlight.js/lib/common'
import 'highlight.js/styles/github-dark.css'

interface ToolCall {
  name: string
  displayName: string
  status: 'running' | 'done'
  arguments?: string
  result?: string
}

interface Message {
  id: number
  question: string
  answer: string
  sources: ChatResponse['sources']
  streaming: boolean
  error: boolean
  renderedHtml: string
  toolCalls: ToolCall[]
}

import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps<{
  messages: Message[]
  loading: boolean
}>()

const emit = defineEmits<{
  'quick-ask': [question: string]
  'preview-source': [documentId: number, documentTitle: string, chunkIndex: number]
}>()

const quickPrompts = [
  '奖学金怎么评定?',
  '图书馆有空座吗?',
  '怎么转专业?',
  '今天天气怎么样?',
]

const bodyRef = ref<HTMLElement>()
const forceUpdateKey = ref(0)

// 配置 marked:代码高亮 + 复制按钮 + 引用编号内联跳转
const renderer = {
  code(token: any) {
    const text: string = token.text || ''
    const lang: string = token.lang || ''
    const language = lang.split(/\s+/)[0]
    let highlighted: string
    if (language && hljs.getLanguage(language)) {
      highlighted = hljs.highlight(text, { language }).value
    } else {
      highlighted = hljs.highlightAuto(text).value
    }
    const encoded = encodeURIComponent(text)
    return `<pre><button class="copy-btn" data-code="${encoded}">复制</button><code class="hljs language-${language}">${highlighted}</code></pre>`
  },
  text(token: any) {
    const text: string = token.text || ''
    // [1] [12] 转成可点击的引用标记，codespan 内的 [1] 不会进 text 钩子
    return text.replace(/\[(\d+)\]/g, '<a class="cite-ref" data-idx="$1">[$1]</a>')
  },
}
marked.use({ renderer })

// 复制按钮 + 引用跳转事件委托(v-html 内容无法用 Vue 绑定,用全局 click 监听)
function handleBodyClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.classList.contains('copy-btn')) {
    const code = decodeURIComponent(target.dataset.code || '')
    navigator.clipboard.writeText(code).then(() => {
      target.textContent = '已复制 ✓'
      setTimeout(() => { target.textContent = '复制' }, 1500)
    }).catch(() => {
      target.textContent = '复制失败'
      setTimeout(() => { target.textContent = '复制' }, 1500)
    })
    return
  }
  if (target.classList.contains('cite-ref')) {
    const idx = target.dataset.idx
    if (!idx) return
    const src = document.getElementById('src-' + idx)
    if (src) {
      src.scrollIntoView({ behavior: 'smooth', block: 'center' })
      src.classList.add('highlight')
      setTimeout(() => src.classList.remove('highlight'), 1500)
    }
  }
}

onMounted(() => {
  document.addEventListener('click', handleBodyClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleBodyClick)
})

function scrollToBottom() {
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  }
}

function refresh() {
  forceUpdateKey.value++
  nextTick(() => scrollToBottom())
}

function getHtml(msg: Message): string {
  if (!msg.answer) return ''
  // 优先使用预渲染的 HTML，如果没有则实时渲染
  if (msg.renderedHtml) return msg.renderedHtml
  try {
    return marked.parse(msg.answer, {
      async: false,
      gfm: true,
      breaks: true,
    }) as string
  } catch {
    return msg.answer.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

function formatJson(str: string | undefined): string {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

function hasDebugInfo(msg: Message): boolean {
  return msg.sources.some(s => s.vectorScore !== undefined || s.esScore !== undefined)
}

// 展开状态受控管理(避免 <details> 原生 open 状态在 Vue 重新渲染时丢失)
const debugOpenMap = ref<Record<number, boolean>>({})
const toolOpenMap = ref<Record<string, boolean>>({})

function toggleDebug(msgId: number) {
  debugOpenMap.value[msgId] = !debugOpenMap.value[msgId]
}

function toggleTool(key: string) {
  toolOpenMap.value[key] = !toolOpenMap.value[key]
}

defineExpose({ scrollToBottom, refresh })
</script>

<template>
  <div ref="bodyRef" class="chat-body">
    <div v-if="messages.length === 0 && !loading" class="chat-empty">
      <div class="empty-icon">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#0f766e" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.55">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          <line x1="9" y1="10" x2="15" y2="10"/>
          <line x1="12" y1="7" x2="12" y2="13"/>
        </svg>
      </div>
      <p class="empty-title">校园知识问答</p>
      <p class="empty-hint">输入问题，或从下面这些开始</p>
      <div class="quick-prompts">
        <button v-for="p in quickPrompts" :key="p" class="prompt-card" @click="emit('quick-ask', p)">
          <span class="prompt-text">{{ p }}</span>
          <span class="prompt-arrow">&rarr;</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading-history">加载历史消息中...</div>

    <TransitionGroup name="msg" tag="div" class="msg-list">
    <div v-for="msg in messages" :key="msg.id" class="chat-item">
      <div class="chat-question">
        <div class="msg-avatar q-avatar">Q</div>
        <div class="msg-bubble q-bubble">{{ msg.question }}</div>
      </div>
      <div class="chat-answer">
        <div class="msg-avatar a-avatar">A</div>
        <div class="msg-content">
          <div class="answer-text" :class="{ 'error-text': msg.error }">
            <div v-if="msg.error" class="error-content"><pre>{{ msg.answer }}</pre></div>
            <div v-else class="markdown-body" v-html="getHtml(msg)"></div>
            <div v-if="msg.toolCalls.length > 0" class="tool-card">
              <div class="tool-card-title">
                <span v-if="msg.streaming" class="tool-spinner"></span>
                <span>{{ msg.streaming ? '正在使用工具' : '已使用工具' }}</span>
              </div>
              <div v-for="(tc, i) in msg.toolCalls" :key="i" class="tool-step">
                <div class="tool-step-header" @click="toggleTool(`${msg.id}-${i}`)">
                  <span class="step-icon" :class="{ done: tc.status === 'done' }">{{ tc.status === 'done' ? '✓' : '⋯' }}</span>
                  <span class="step-name">{{ tc.displayName }}</span>
                  <span v-if="tc.arguments || tc.result" class="step-expand-hint">{{ toolOpenMap[`${msg.id}-${i}`] ? '点击收起' : '点击展开' }}</span>
                </div>
                <div v-show="toolOpenMap[`${msg.id}-${i}`]" class="tool-step-body">
                  <div v-if="tc.arguments" class="tool-detail">
                    <div class="tool-detail-label">入参</div>
                    <pre class="tool-detail-code">{{ formatJson(tc.arguments) }}</pre>
                  </div>
                  <div v-if="tc.result" class="tool-detail">
                    <div class="tool-detail-label">返回</div>
                    <pre class="tool-detail-code">{{ formatJson(tc.result) }}</pre>
                  </div>
                </div>
              </div>
            </div>
            <span v-if="msg.streaming && msg.toolCalls.length === 0" class="stream-cursor">|</span>
          </div>
          <div v-if="!msg.streaming && !msg.error && msg.sources.length > 0" class="sources-box">
            <div class="sources-title">引用来源</div>
            <div v-for="(src, i) in msg.sources" :key="i" class="source-item" :id="'src-' + (i + 1)" @click="emit('preview-source', src.documentId, src.documentTitle, src.chunkIndex)">
              <span class="source-idx">[{{ i + 1 }}]</span>
              <span class="source-name">{{ src.documentTitle }}</span>
              <span class="source-score">{{ (src.similarityScore * 100).toFixed(0) }}%</span>
            </div>
            <div v-if="hasDebugInfo(msg)" class="debug-panel">
              <div class="debug-summary" @click="toggleDebug(msg.id)">
                <span class="debug-arrow" :class="{ open: !!debugOpenMap[msg.id] }">▸</span>
                <span>{{ debugOpenMap[msg.id] ? '收起检索调试信息' : '展开检索调试信息（向量 / ES / RRF 三路对比）' }}</span>
              </div>
              <div v-show="debugOpenMap[msg.id]" class="debug-table">
                <div class="debug-row debug-header">
                  <span>#</span>
                  <span>文档</span>
                  <span>向量分</span>
                  <span>向量排名</span>
                  <span>ES 分</span>
                  <span>ES 排名</span>
                  <span>RRF 分</span>
                </div>
                <div v-for="(src, i) in msg.sources" :key="'d'+i" class="debug-row">
                  <span>{{ i + 1 }}</span>
                  <span class="debug-doc" :title="src.documentTitle">{{ src.documentTitle }}</span>
                  <span>{{ src.vectorScore != null ? src.vectorScore.toFixed(4) : '—' }}</span>
                  <span class="debug-rank">{{ src.vectorRank ? '#' + src.vectorRank : '—' }}</span>
                  <span>{{ src.esScore != null ? src.esScore.toFixed(4) : '—' }}</span>
                  <span class="debug-rank">{{ src.esRank ? '#' + src.esRank : '—' }}</span>
                  <span class="debug-rrf">{{ src.similarityScore.toFixed(4) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 24px 24px;
}

.chat-empty { text-align: center; padding-top: 80px; }
.loading-history { text-align: center; padding: 24px; font-size: 14px; color: var(--text-secondary); }
.empty-icon { margin-bottom: 16px; }

.empty-title { font-size: 18px; font-weight: 600; color: var(--text); margin-bottom: 8px; }
.empty-hint { font-size: 14px; color: var(--text-secondary); }

/* 快捷提问卡片 - 图书馆索引卡风格 */
.quick-prompts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  max-width: 560px;
  margin: 24px auto 0;
}

.prompt-card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 14px 18px;
  text-align: left;
  background: var(--white);
  border: 1px solid var(--border);
  border-radius: 4px;
  cursor: pointer;
  font-size: 13.5px;
  color: var(--text);
  font-family: inherit;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.prompt-card::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--green);
  transition: width 0.25s ease;
}

.prompt-card:hover {
  background: var(--green-bg);
  border-color: var(--green);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px -2px rgba(15, 118, 110, 0.15);
}

.prompt-card:hover::before { width: 5px; }

.prompt-card:active { transform: translateY(0) scale(0.98); }
.prompt-card:active::before { width: 3px; }

.prompt-text { flex: 1; }

.prompt-arrow {
  color: #a8a29e;
  font-size: 14px;
  transition: transform 0.25s, color 0.25s;
  flex-shrink: 0;
  margin-left: 8px;
}

.prompt-card:hover .prompt-arrow {
  color: var(--green);
  transform: translateX(3px);
}

.chat-item {
  margin-bottom: 18px;
  max-width: 820px;
  margin-left: 0;
  margin-right: auto;
}

.msg-list {
  display: block;
}

/* 消息进入动画 */
.msg-enter-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.msg-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.chat-question, .chat-answer { display: flex; gap: 12px; margin-bottom: 8px; }

.msg-avatar {
  width: 36px; height: 36px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; flex-shrink: 0;
}

.q-avatar { background: var(--green-bg); color: var(--green); }
.a-avatar { background: var(--accent-bg); color: var(--accent); }

.msg-bubble {
  padding: 10px 14px; border-radius: 10px;
  font-size: 14px; line-height: 1.6;
}

.q-bubble { background: #f3f4f6; color: var(--text); }

.msg-content { flex: 1; min-width: 0; }

.answer-text pre {
  white-space: pre-wrap; word-break: break-word;
  font-family: inherit; font-size: 14px; line-height: 1.7; margin: 0;
}

.error-content pre { color: #ef4444; white-space: pre-wrap; word-break: break-word; font-family: inherit; font-size: 14px; line-height: 1.7; margin: 0; }

.markdown-body { font-size: 14px; line-height: 1.7; color: var(--text); }

.markdown-body :deep(h1), .markdown-body :deep(h2),
.markdown-body :deep(h3), .markdown-body :deep(h4) {
  margin: 16px 0 8px; font-weight: 600; color: var(--text);
}

.markdown-body :deep(h2) { font-size: 17px; padding-bottom: 6px; border-bottom: 1px solid var(--border); }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin-bottom: 4px; }

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 14px;
  border-left: 3px solid var(--green);
  background: var(--green-bg);
  color: var(--text-secondary);
}

/* 移除嵌套 blockquote 的左边框和背景，避免重复显示 */
.markdown-body :deep(blockquote blockquote),
.markdown-body :deep(blockquote > blockquote) {
  border-left: none !important;
  background: transparent !important;
  margin: 0 !important;
  padding: 0 !important;
}

.markdown-body :deep(code) {
  padding: 1px 5px; font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  background: #f3f4f6; border-radius: 3px;
}

.markdown-body :deep(pre) {
  position: relative;
  margin: 10px 0; padding: 12px 16px; font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  background: #0d1117; color: #c9d1d9; border-radius: 6px; overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0; font-size: inherit; background: transparent; border-radius: 0; color: inherit;
}

.markdown-body :deep(pre code.hljs) {
  background: transparent; padding: 0;
}

.markdown-body :deep(.copy-btn) {
  position: absolute;
  top: 8px; right: 8px;
  background: rgba(255, 255, 255, 0.08);
  color: #c9d1d9;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  padding: 3px 10px;
  font-size: 11px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s, background 0.2s;
  font-family: inherit;
}

.markdown-body :deep(pre:hover .copy-btn) { opacity: 1; }

.markdown-body :deep(.copy-btn:hover) {
  background: rgba(255, 255, 255, 0.15);
}

.markdown-body :deep(table) { width: 100%; margin: 10px 0; border-collapse: collapse; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 8px 12px; border: 1px solid var(--border); text-align: left; }
.markdown-body :deep(th) { background: #f9fafb; font-weight: 600; }
.markdown-body :deep(tr:nth-child(even)) { background: #f9fafb; }
.markdown-body :deep(a) { color: var(--green); text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(hr) { margin: 14px 0; border: none; border-top: 1px solid var(--border); }
.markdown-body :deep(strong) { font-weight: 600; color: var(--text); }

.sources-box {
  margin-top: 12px; padding: 12px 14px;
  background: var(--white); border: 1px solid var(--border); border-radius: 8px;
}

.sources-title {
  font-size: 12px; font-weight: 600; color: var(--text-secondary);
  margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text);
  margin-bottom: 3px;
  padding: 4px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}
.source-item:hover {
  background: var(--green-bg);
}
.source-item.highlight {
  animation: src-highlight 1.5s ease;
  border-radius: 4px;
}
@keyframes src-highlight {
  0%, 100% { background: transparent; }
  15%, 70% { background: var(--green-bg); }
}
.source-idx { color: var(--green); font-weight: 600; font-size: 12px; }
.source-name { flex: 1; }
.source-score { color: var(--text-secondary); font-size: 11px; background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }

/* 检索调试面板 */
.debug-panel {
  margin-top: 10px;
  border-top: 1px dashed var(--border);
  padding-top: 8px;
}
.debug-summary {
  display: flex;
  align-items: center;
  cursor: pointer;
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 600;
  letter-spacing: 0.3px;
  padding: 2px 0;
  user-select: none;
  transition: color 0.15s;
}
.debug-summary:hover { color: var(--green); }
.debug-arrow {
  display: inline-block;
  margin-right: 6px;
  transition: transform 0.2s;
  color: var(--green);
  font-size: 10px;
}
.debug-arrow.open { transform: rotate(90deg); }
.debug-table {
  margin-top: 8px;
  font-size: 11px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
}
.debug-row {
  display: grid;
  grid-template-columns: 24px 1fr 64px 56px 64px 56px 64px;
  gap: 6px;
  padding: 4px 2px;
  border-bottom: 1px solid var(--border);
  align-items: center;
}
.debug-row:last-child { border-bottom: none; }
.debug-header {
  font-weight: 600;
  color: var(--text-secondary);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.debug-doc {
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.debug-rank {
  color: var(--accent);
  font-weight: 600;
}
.debug-rrf {
  color: var(--green);
  font-weight: 600;
}

.markdown-body :deep(.cite-ref) {
  color: var(--green);
  font-weight: 600;
  cursor: pointer;
  padding: 0 1px;
  transition: color 0.15s;
}
.markdown-body :deep(.cite-ref:hover) {
  color: var(--green-hover);
  text-decoration: underline;
}

.stream-cursor { color: var(--green); font-weight: 400; animation: blink 0.8s infinite; }

.tool-card {
  margin-top: 10px; padding: 10px 12px;
  background: var(--accent-bg); border: 1px solid var(--accent-light);
  border-radius: 8px; font-size: 13px;
}

.tool-card-title {
  display: flex; align-items: center; gap: 6px;
  font-weight: 600; color: var(--accent); margin-bottom: 6px;
}

.tool-step {
  margin: 2px 0;
  border-radius: 4px;
}
.tool-step-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.tool-step-header:hover { background: rgba(255, 255, 255, 0.4); }
.tool-step-body { padding-top: 2px; }

.step-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px;
  color: var(--accent); font-weight: 700; font-size: 12px;
}
.step-icon.done { color: var(--green); }

.step-name { color: var(--text); flex: 1; }

.step-expand-hint {
  font-size: 11px;
  color: var(--text-secondary);
  opacity: 0.55;
}
.tool-step[open] .step-expand-hint { display: none; }

.tool-detail {
  margin: 4px 0 6px 28px;
  padding: 6px 10px;
  border-left: 2px solid var(--accent-light);
  background: rgba(255, 255, 255, 0.6);
  border-radius: 0 4px 4px 0;
}
.tool-detail-label {
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}
.tool-detail-code {
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text);
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
}

.tool-spinner {
  width: 14px; height: 14px;
  border: 2px solid var(--accent-light);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  display: inline-block;
}

@keyframes spin { to { transform: rotate(360deg); } }

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>