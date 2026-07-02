package com.zrlog.plugin.statistics.model;

public class StatisticsConfigValues {

    private String host;
    private String statisticsRetentionDays;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getStatisticsRetentionDays() {
        return statisticsRetentionDays;
    }

    public void setStatisticsRetentionDays(String statisticsRetentionDays) {
        this.statisticsRetentionDays = statisticsRetentionDays;
    }
}
