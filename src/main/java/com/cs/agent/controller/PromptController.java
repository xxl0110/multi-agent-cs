package com.cs.agent.controller;

import com.cs.agent.entity.PromptTemplate;
import com.cs.agent.service.PromptService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PromptController.class);

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public List<PromptTemplate> list() { return promptService.list(); }

    @GetMapping("/defaults")
    public List<String> getDefaultWorkers() { return promptService.getDefaultWorkers(); }

    @PostMapping
    public PromptTemplate create(@RequestBody PromptTemplate template) {
        return promptService.create(template);
    }

    @PutMapping("/{id}")
    public PromptTemplate update(@PathVariable String id, @RequestBody PromptTemplate template) {
        PromptTemplate updated = promptService.update(id, template);
        if (updated == null) throw new RuntimeException("Prompt 不存在: " + id);
        return updated;
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        promptService.delete(id);
        return Map.of("status", "deleted", "id", id);
    }

    @GetMapping("/{id}")
    public PromptTemplate get(@PathVariable String id) {
        PromptTemplate t = promptService.get(id);
        if (t == null) throw new RuntimeException("Prompt 不存在: " + id);
        return t;
    }
}
