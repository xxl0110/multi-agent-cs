package com.cs.agent.interceptor;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 敏感词管理服务
 */
@Service
public class SensitiveWordService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SensitiveWordService.class);

    private final Set<String> blockedWords = ConcurrentHashMap.newKeySet();
    private volatile Pattern blockedPattern = Pattern.compile("a^"); // 不匹配任何内容

    public SensitiveWordService() {
        // 默认敏感词
        addBlockedWords(List.of("诈骗", "赌博", "色情", "毒品", "枪支", "暴力"));
        log.info("🔒 敏感词服务初始化，默认 {} 条", blockedWords.size());
    }

    public void addBlockedWords(List<String> words) {
        blockedWords.addAll(words);
        rebuildPattern();
    }

    public void removeBlockedWords(List<String> words) {
        words.forEach(blockedWords::remove);
        rebuildPattern();
    }

    public List<String> getBlockedWords() {
        return new ArrayList<>(blockedWords);
    }

    /** 检查文本是否包含敏感词，返回命中的词列表 */
    public List<String> check(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> hits = new ArrayList<>();
        for (String word : blockedWords) {
            if (text.contains(word)) {
                hits.add(word);
            }
        }
        return hits;
    }

    /** 是否包含敏感词 */
    public boolean containsBlocked(String text) {
        return !check(text).isEmpty();
    }

    private void rebuildPattern() {
        // 简单的正则匹配
        String joined = String.join("|", blockedWords.stream().map(Pattern::quote).toList());
        this.blockedPattern = joined.isEmpty() ? Pattern.compile("a^") : Pattern.compile(joined);
    }
}
