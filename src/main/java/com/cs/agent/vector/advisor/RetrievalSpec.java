package com.cs.agent.vector.advisor;

/**
 * 检索规格 —— 控制检索的目标集合、过滤条件、数量等
 */
public class RetrievalSpec {
    private final int topK;
    private final double minScore;
    private final String expr;

    public RetrievalSpec(int topK, double minScore, String expr) {
        this.topK = topK;
        this.minScore = minScore;
        this.expr = expr;
    }

    public int getTopK() { return topK; }
    public double getMinScore() { return minScore; }
    public String getExpr() { return expr; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int topK = 3;
        private double minScore = 0.6;
        private String expr = "";

        public Builder topK(int topK) { this.topK = topK; return this; }
        public Builder minScore(double minScore) { this.minScore = minScore; return this; }
        public Builder expr(String expr) { this.expr = expr; return this; }
        public RetrievalSpec build() { return new RetrievalSpec(topK, minScore, expr); }
    }
}
