package com.zrlog.plugin.statistics.model;

public class StatisticsArticleVisitRequest {

    private String alias;

    public StatisticsArticleVisitRequest() {
    }

    public StatisticsArticleVisitRequest(String alias) {
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
