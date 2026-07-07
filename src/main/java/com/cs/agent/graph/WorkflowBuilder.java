package com.cs.agent.graph;

import com.cs.agent.node.SupervisorNode;
import com.cs.agent.node.agent.ComplaintAgentNode;
import com.cs.agent.node.agent.OrderAgentNode;
import com.cs.agent.node.agent.ProductAgentNode;
import com.cs.agent.node.agent.ReturnAgentNode;
import com.cs.agent.node.common.FinishNode;
import com.cs.agent.state.CsAgentState;
import jakarta.annotation.PostConstruct;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * StateGraph 工作流构建器
 *
 * 图结构：
 *   START → supervisor → (条件路由) → Worker
 *                                      → supervisor (循环)
 *                                      → finish → END
 */

@Component
public class WorkflowBuilder {
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowBuilder.class);

    @Autowired private SupervisorNode supervisorNode;
    @Autowired private OrderAgentNode orderAgentNode;
    @Autowired private ProductAgentNode productAgentNode;
    @Autowired private ReturnAgentNode returnAgentNode;
    @Autowired private ComplaintAgentNode complaintAgentNode;
    @Autowired private FinishNode finishNode;

    private CompiledGraph<CsAgentState> compiledGraph;

    @PostConstruct
    public void init() {
        try {
            this.compiledGraph = buildGraph().compile();
            log.info("✅ StateGraph 编译完成！");
        } catch (Exception e) {
            log.error("❌ StateGraph 编译失败", e);
            throw new RuntimeException(e);
        }
    }

    public StateGraph<CsAgentState> buildGraph() {
        try {
            StateGraph<CsAgentState> graph = new StateGraph<>(CsAgentState.SCHEMA, CsAgentState::new);

            // 1️⃣ 注册节点（Node 必须包装为 AsyncNodeAction）
            graph.addNode("supervisor", AsyncNodeAction.node_async(supervisorNode));
            graph.addNode("order_agent", AsyncNodeAction.node_async(orderAgentNode));
            graph.addNode("product_agent", AsyncNodeAction.node_async(productAgentNode));
            graph.addNode("return_agent", AsyncNodeAction.node_async(returnAgentNode));
            graph.addNode("complaint_agent", AsyncNodeAction.node_async(complaintAgentNode));
            graph.addNode("finish", AsyncNodeAction.node_async(finishNode));

            // 2️⃣ 入口边
            graph.addEdge(START, "supervisor");

            // 3️⃣ Supervisor 条件路由
            AsyncEdgeAction<CsAgentState> router = edge_async(state -> state.next());
            graph.addConditionalEdges("supervisor", router, Map.of(
                    "order_agent", "order_agent",
                    "product_agent", "product_agent",
                    "return_agent", "return_agent",
                    "complaint_agent", "complaint_agent",
                    "FINISH", "finish"
            ));

            // 4️⃣ Worker 执行完回到 Supervisor（循环）
            graph.addEdge("order_agent", "supervisor");
            graph.addEdge("product_agent", "supervisor");
            graph.addEdge("return_agent", "supervisor");
            graph.addEdge("complaint_agent", "supervisor");

            // 5️⃣ 结束
            graph.addEdge("finish", END);

            return graph;
        } catch (Exception e) {
            throw new RuntimeException("构建 StateGraph 失败", e);
        }
    }

    public CompiledGraph<CsAgentState> getCompiledGraph() {
        return compiledGraph;
    }
}
