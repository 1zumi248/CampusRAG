package com.hznu.campusragbackend.service;

import com.hznu.campusragbackend.model.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    
    /**
     * 上传并解析文档
     * @param file 上传的文件
     * @return 保存的文档信息
     */
    Document uploadDocument(MultipartFile file);
    
    /**
     * 获取所有文档列表
     * @return 文档列表
     */
    List<Document> listDocuments();
    
    /**
     * 根据 ID 获取文档
     * @param id 文档 ID
     * @return 文档信息
     */
    Document getDocumentById(Long id);
    
    /**
     * 删除文档
     * @param id 文档 ID
     */
    void deleteDocument(Long id);
}
