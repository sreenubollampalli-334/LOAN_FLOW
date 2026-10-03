package com.loanflow.document.controller;

import com.loanflow.document.dto.DocumentMetadataResponse;
import com.loanflow.document.service.DocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loan-applications/{applicationId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<DocumentMetadataResponse> uploadDocument(
            @PathVariable Long applicationId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        DocumentMetadataResponse response = documentService.uploadDocument(applicationId, documentType, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<DocumentMetadataResponse>> getApplicationDocuments(@PathVariable Long applicationId) {
        List<DocumentMetadataResponse> documents = documentService.getApplicationDocuments(applicationId);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<DocumentMetadataResponse> getDocumentMetadata(
            @PathVariable Long applicationId,
            @PathVariable Long documentId) {
        DocumentMetadataResponse response = documentService.getDocumentMetadata(applicationId, documentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CREDIT_OFFICER', 'MANAGER')")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId) {
        return documentService.downloadDocument(applicationId, documentId);
    }
}
