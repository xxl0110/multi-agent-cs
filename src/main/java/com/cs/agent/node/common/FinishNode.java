package com.cs.agent.node.common;

import com.cs.agent.state.CsAgentState;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结束节点 —— 流程终点
 *
 * 这个节点不做任何事，只是让 StateGraph 有一个明确的 END 前置节点。
 * 所有边最终汇聚到这里。
 */

@Component
public class FinishNode implements NodeAction<CsAgentState> {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FinishNode.class);

    @Override
    public Map<String, Object> apply(CsAgentState state) {
        log.info("🏁 [Finish] 客服对话流程结束");
        return Map.of();
    }
}
