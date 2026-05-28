package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import com.hznu.campusragbackend.model.Document;
import com.hznu.campusragbackend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.hznu.campusragbackend.common.PageResult;

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
     * 获取文档列表（分页）
     * @param page 页码，默认1
     * @param pageSize 每页数量，默认10
     */
    @GetMapping
    public Result<PageResult<Document>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(documentService.listDocuments(page, pageSize));
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
