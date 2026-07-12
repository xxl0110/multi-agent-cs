package com.cs.agent.controller;

import com.cs.agent.vector.advisor.RetrievalSpec;
import com.cs.agent.vector.file.FileUploadService;
import com.cs.agent.vector.file.dto.UploadResult;
import com.cs.agent.vector.store.KnowledgeBaseStore;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeController.class);

    private final FileUploadService fileUploadService;
    private final KnowledgeBaseStore knowledgeBaseStore;

    public KnowledgeController(FileUploadService fileUploadService, KnowledgeBaseStore knowledgeBaseStore) {
        this.fileUploadService = fileUploadService;
        this.knowledgeBaseStore = knowledgeBaseStore;
    }

    @PostMapping("/upload")
    public UploadResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "other") String category,
            @RequestParam(value = "tags", defaultValue = "") String tags) {
        return fileUploadService.upload(file, category, tags);
    }

    @GetMapping("/documents")
    public List<String> listDocuments() {
        return knowledgeBaseStore.listDocuments();
    }

    @DeleteMapping("/documents/{docId}")
    public Map<String, Object> deleteDocument(@PathVariable String docId) {
        knowledgeBaseStore.deleteDocument(docId);
        return Map.of("docId", docId, "status", "deleted");
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.6") double minScore) {
        var spec = RetrievalSpec.builder().topK(topK).minScore(minScore).build();
        var ctx = knowledgeBaseStore.searchKnowledge(q, spec);
        return Map.of("query", q, "results",
                ctx.getDocuments().stream().map(d -> Map.of(
                        "chunkId", d.getChunkId(),
                        "content", d.getContent().length() > 200 ? d.getContent().substring(0, 200) : d.getContent(),
                        "score", d.getScore(),
                        "metadata", d.getMetadataJson()
                )).toList());
    }

    @GetMapping("/collections")
    public Map<String, Object> getStats() {
        return knowledgeBaseStore.getStats();
    }
}
