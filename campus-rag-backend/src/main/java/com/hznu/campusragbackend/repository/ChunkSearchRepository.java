package com.hznu.campusragbackend.repository;

import com.hznu.campusragbackend.model.ChunkDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkSearchRepository extends ElasticsearchRepository<ChunkDocument, String> {

    List<ChunkDocument> findByDocumentId(Long documentId);
}