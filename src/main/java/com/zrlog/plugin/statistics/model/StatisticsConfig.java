package com.zrlog.plugin.statistics.model;

public class StatisticsConfig {

    private String host = "";
    private int retentionDays = 30;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
