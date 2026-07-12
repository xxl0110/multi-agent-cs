package com.cs.agent.service;

import com.cs.agent.entity.PromptTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板管理 —— 管理员通过 API 动态管理 Worker 的 System Prompt
 */
@Service
public class PromptService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PromptService.class);

    private final Map<String, PromptTemplate> store = new ConcurrentHashMap<>();
    /** 默认 prompt 缓存：workerName → prompt */
    private final Map<String, String> defaultPrompts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册默认 Prompt（与现有硬编码保持一致）
        registerDefault("supervisor",
                "你是一个电商客服系统的智能调度员（Supervisor）。\n\n你的职责：\n1. 分析用户的输入，判断应该由哪个专用客服来处理\n2. **只有第一轮才需要路由**，后续轮次都输出 FINISH\n3. 注意：如果用户输入的是订单号（ORD开头的数字），这通常是上一步被问到的补充信息\n\n可用 Worker（只能从以下选择）：\n- order_agent: 订单查询、物流查询、订单修改\n- product_agent: 商品搜索、商品推荐、商品详情查询\n- return_agent: 退换货申请、退款计算、退换货进度查询\n- complaint_agent: 投诉处理、升级人工客服\n\n输出格式（严格 JSON，不要多余内容）：\n{\"next\": \"order_agent\"}\n{\"next\": \"product_agent\"}\n{\"next\": \"return_agent\"}\n{\"next\": \"complaint_agent\"}\n{\"next\": \"FINISH\"}");
        registerDefault("product_agent",
                "你是电商客服系统的商品专员。\n根据参考知识和用户问题推荐合适的商品。\n规则：\n- 回答简洁友好，用中文\n- 优先参考提供的知识内容");
        registerDefault("return_agent",
                "你是电商客服系统的退换货专员。\n根据参考知识回答退换货相关问题。\n规则：\n- 用户需要提供订单号才能查询\n- 回答简洁友好，用中文\n- 优先参考提供的知识内容");
        registerDefault("complaint_agent",
                "你是电商客服系统的投诉专员。\n根据参考知识处理用户投诉。\n规则：\n- 先安抚用户情绪，再记录问题\n- 承诺会尽快处理并反馈\n- 回答有礼貌，用中文\n- 优先参考提供的知识内容");
        registerDefault("order_agent",
                "你是电商客服系统的订单专员。\n根据用户问题和查询到的数据生成回答，简洁友好，用中文。");

        log.info("📝 PromptService 初始化完成，已注册 {} 个默认角色", defaultPrompts.size());
    }

    private void registerDefault(String workerName, String prompt) {
        defaultPrompts.put(workerName, prompt);
    }

    /** 获取 Worker 的 System Prompt — 优先使用管理员配置的自定义 Prompt，没有则返回默认 */
    public String getPrompt(String workerName) {
        // 找到该 worker 已启用的自定义 prompt
        for (PromptTemplate t : store.values()) {
            if (t.getTargetWorker().equals(workerName) && t.isEnabled()) {
                return t.getSystemPrompt();
            }
        }
        return defaultPrompts.get(workerName);
    }

    /** 创建自定义 Prompt */
    public PromptTemplate create(PromptTemplate template) {
        String id = "prompt_" + UUID.randomUUID().toString().substring(0, 8);
        template.setId(id);
        store.put(id, template);
        log.info("📝 创建 Prompt: id={}, name={}, worker={}", id, template.getName(), template.getTargetWorker());
        return template;
    }

    /** 更新 Prompt */
    public PromptTemplate update(String id, PromptTemplate update) {
        PromptTemplate existing = store.get(id);
        if (existing == null) return null;
        existing.setName(update.getName());
        existing.setSystemPrompt(update.getSystemPrompt());
        existing.setDescription(update.getDescription());
        existing.setEnabled(update.isEnabled());
        log.info("📝 更新 Prompt: id={}", id);
        return existing;
    }

    /** 删除 Prompt */
    public void delete(String id) {
        store.remove(id);
        log.info("📝 删除 Prompt: id={}", id);
    }

    /** 获取所有自定义 Prompt */
    public List<PromptTemplate> list() {
        return new ArrayList<>(store.values());
    }

    /** 获取单个 */
    public PromptTemplate get(String id) { return store.get(id); }

    /** 获取所有默认 Prompt 名称 */
    public List<String> getDefaultWorkers() {
        return new ArrayList<>(defaultPrompts.keySet());
    }
}
