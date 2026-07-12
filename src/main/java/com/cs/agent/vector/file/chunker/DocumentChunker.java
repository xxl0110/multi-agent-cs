package com.cs.agent.vector.file.chunker;

import com.cs.agent.vector.file.dto.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentChunker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentChunker.class);

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentChunker(@Value("${knowledge.chunk.size:500}") int chunkSize,
                           @Value("${knowledge.chunk.overlap:50}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<DocumentChunk> chunk(String text, String docId) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        int chunkIndex = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.length() > chunkSize * 2 && trimmed.contains("。")) {
                if (current.length() > 0) {
                    chunks.add(createChunk(docId, chunkIndex++, current.toString().trim()));
                    current.setLength(0);
                }
                String[] sentences = trimmed.split("(?<=。|！|？)");
                for (String sentence : sentences) {
                    String s = sentence.trim();
                    if (s.isEmpty()) continue;
                    if (current.length() + s.length() > chunkSize && current.length() > 0) {
                        chunks.add(createChunk(docId, chunkIndex++, current.toString().trim()));
                        current.setLength(0);
                    }
                    current.append(s);
                }
            } else {
                if (current.length() > 0 && current.length() + trimmed.length() > chunkSize) {
                    chunks.add(createChunk(docId, chunkIndex++, current.toString().trim()));
                    String overlap = current.length() > chunkOverlap
                            ? current.substring(current.length() - chunkOverlap)
                            : current.toString();
                    current.setLength(0);
                    current.append(overlap);
                }
                current.append("\n").append(trimmed);
            }
        }

        if (current.length() > 0) {
            chunks.add(createChunk(docId, chunkIndex, current.toString().trim()));
        }

        log.info("📄 切片完成: docId={}, chunks={}", docId, chunks.size());
        return chunks;
    }

    private DocumentChunk createChunk(String docId, int index, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId(docId + "_" + index);
        chunk.setDocId(docId);
        chunk.setContent(content);
        chunk.setChunkIndex(index);
        return chunk;
    }
}
