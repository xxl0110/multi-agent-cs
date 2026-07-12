package com.cs.agent.vector.file.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TextParser implements DocumentParser {
    @Override
    public String parse(byte[] fileContent, String fileName) {
        return new String(fileContent, StandardCharsets.UTF_8);
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of("txt");
    }
}
