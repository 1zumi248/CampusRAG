package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import com.hznu.campusragbackend.model.Document;
import com.hznu.campusragbackend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public Result<Document> uploadDocument(@RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.uploadDocument(file));
    }
    
    /**
     * 获取文档列表
     */
    @GetMapping
    public Result<List<Document>> listDocuments() {
        return Result.ok(documentService.listDocuments());
    }
    
    /**
     * 根据 ID 获取文档
     */
    @GetMapping("/{id}")
    public Result<Document> getDocumentById(@PathVariable Long id) {
        return Result.ok(documentService.getDocumentById(id));
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.ok(null);
    }
}
