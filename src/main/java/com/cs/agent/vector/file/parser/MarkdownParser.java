package com.cs.agent.vector.file.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownParser implements DocumentParser {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");
    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^]]*]\\([^)]+\\)");

    @Override
    public String parse(byte[] fileContent, String fileName) {
        String content = new String(fileContent, StandardCharsets.UTF_8);
        content = CODE_BLOCK.matcher(content).replaceAll("");
        Matcher m = HEADING.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "【h" + m.group(1).length() + "】" + m.group(2));
        }
        m.appendTail(sb);
        content = LINK.matcher(sb.toString()).replaceAll("$1");
        content = IMAGE.matcher(content).replaceAll("");
        content = content.replaceAll("[*_~`>|]", "");
        return content.trim();
    }

    @Override
    public List<String> supportedExtensions() {
        return List.of("md", "markdown");
    }
}
