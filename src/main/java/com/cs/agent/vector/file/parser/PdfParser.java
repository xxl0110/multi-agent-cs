package com.cs.agent.vector.file.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PdfParser implements DocumentParser {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PdfParser.class);

    @Override
    public String parse(byte[] fileContent, String fileName) {
        try (PDDocument doc = Loader.loadPDF(fileContent)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (Exception e) {
            log.error("PDF 解析失败: {}", fileName, e);
            return "【PDF 解析失败: " + e.getMessage() + "】";
        }
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of("pdf");
    }
}
