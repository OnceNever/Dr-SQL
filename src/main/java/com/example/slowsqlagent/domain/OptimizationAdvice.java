package com.example.slowsqlagent.domain;

public class OptimizationAdvice {

    private int priority;
    private String title;
    private String strategy;
    private String expectedBenefit;
    private String riskLevel;
    private String validationSql;
    private String rollbackHint;

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getExpectedBenefit() {
        return expectedBenefit;
    }

    public void setExpectedBenefit(String expectedBenefit) {
        this.expectedBenefit = expectedBenefit;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getValidationSql() {
        return validationSql;
    }

    public void setValidationSql(String validationSql) {
        this.validationSql = validationSql;
    }

    public String getRollbackHint() {
        return rollbackHint;
    }

    public void setRollbackHint(String rollbackHint) {
        this.rollbackHint = rollbackHint;
    }
}
