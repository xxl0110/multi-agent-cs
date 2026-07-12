package com.cs.agent.controller;

import com.cs.agent.interceptor.SensitiveWordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sensitive")
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    public SensitiveWordController(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    @GetMapping
    public List<String> list() { return sensitiveWordService.getBlockedWords(); }

    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, List<String>> body) {
        sensitiveWordService.addBlockedWords(body.get("words"));
        return Map.of("added", body.get("words"), "total", sensitiveWordService.getBlockedWords().size());
    }

    @DeleteMapping
    public Map<String, Object> remove(@RequestBody Map<String, List<String>> body) {
        sensitiveWordService.removeBlockedWords(body.get("words"));
        return Map.of("removed", body.get("words"), "total", sensitiveWordService.getBlockedWords().size());
    }
}
