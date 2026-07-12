package com.cs.agent.vector.file.parser;

import java.util.List;

public interface DocumentParser {
    String parse(byte[] fileContent, String fileName);
    List<String> supportedExtensions();
}
