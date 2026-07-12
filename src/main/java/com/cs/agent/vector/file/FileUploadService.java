package com.cs.agent.vector.file;

import com.cs.agent.vector.EmbeddingService;
import com.cs.agent.vector.MilvusClientWrapper;
import com.cs.agent.vector.MilvusClientWrapper.KnowledgeChunk;
import com.cs.agent.vector.file.chunker.DocumentChunker;
import com.cs.agent.vector.file.dto.DocumentChunk;
import com.cs.agent.vector.file.dto.UploadResult;
import com.cs.agent.vector.file.parser.DocumentParser;
import com.cs.agent.vector.file.parser.MarkdownParser;
import com.cs.agent.vector.file.parser.PdfParser;
import com.cs.agent.vector.file.parser.TextParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FileUploadService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileUploadService.class);

    private final PdfParser pdfParser;
    private final TextParser textParser;
    private final MarkdownParser markdownParser;
    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final MilvusClientWrapper milvusClientWrapper;

    @Value("${knowledge.upload.max-file-size:10485760}")
    private long maxFileSize;

    @Value("${knowledge.upload.storage-path:./knowledge/files}")
    private String storagePath;

    public FileUploadService(PdfParser pdfParser, TextParser textParser, MarkdownParser markdownParser,
                             DocumentChunker chunker, EmbeddingService embeddingService,
                             MilvusClientWrapper milvusClientWrapper) {
        this.pdfParser = pdfParser;
        this.textParser = textParser;
        this.markdownParser = markdownParser;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.milvusClientWrapper = milvusClientWrapper;
    }

    public UploadResult upload(MultipartFile file, String category, String tags) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件大小超过限制 (" + maxFileSize / 1024 / 1024 + "MB)");
        }

        String ext = getExtension(fileName);
        DocumentParser parser = getParser(ext);
        if (parser == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        }

        String text;
        try {
            text = parser.parse(file.getBytes(), fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + fileName, e);
        }

        String docId = "kb_" + UUID.randomUUID().toString().substring(0, 8);
        saveOriginalFile(file, docId, ext);

        List<DocumentChunk> chunks = chunker.chunk(text, docId);
        List<String> chunkTexts = chunks.stream().map(DocumentChunk::getContent).toList();
        List<List<Float>> embeddings = embeddingService.embedBatch(chunkTexts);

        String metadataJson = buildMetadataJson(fileName, category, tags, chunks.size());

        List<KnowledgeChunk> knowledgeChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            knowledgeChunks.add(new KnowledgeChunk(
                    chunk.getChunkId(), chunk.getContent(), embeddings.get(i), metadataJson));
        }
        milvusClientWrapper.insertChunks(knowledgeChunks);

        UploadResult result = new UploadResult();
        result.setDocId(docId);
        result.setFileName(fileName);
        result.setChunks(chunks.size());
        result.setStatus("indexed");
        result.setCollection("cs_knowledge");
        result.setUploadedAt(LocalDateTime.now());

        log.info("📤 文件上传完成: {} → docId={}, chunks={}", fileName, docId, chunks.size());
        return result;
    }

    private DocumentParser getParser(String ext) {
        return switch (ext.toLowerCase()) {
            case "pdf" -> pdfParser;
            case "txt" -> textParser;
            case "md", "markdown" -> markdownParser;
            default -> null;
        };
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1);
    }

    private void saveOriginalFile(MultipartFile file, String docId, String ext) {
        try {
            Path dir = Paths.get(storagePath, ext);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(docId + "." + ext), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("原始文件保存失败: {}", e.getMessage());
        }
    }

    private String buildMetadataJson(String fileName, String category, String tags, int totalChunks) {
        String safeCategory = (category != null && !category.isBlank()) ? category : "other";
        return String.format(
                "{\"sourceFile\":\"%s\",\"category\":\"%s\",\"tags\":\"%s\",\"totalChunks\":%d,\"uploadedAt\":\"%s\"}",
                fileName, safeCategory, tags != null ? tags : "", totalChunks, LocalDateTime.now().toString());
    }
}
