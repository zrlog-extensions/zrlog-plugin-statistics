package com.zrlog.plugin.statistics.model;

import com.zrlog.plugin.message.Plugin;

public class StatisticsPageData {

    private boolean dark;
    private String colorPrimary;
    private Plugin plugin;
    private StatisticsConfig config;
    private StatisticsNotificationChannels notificationChannels;
    private Object summary;
    private Object charts;
    private Object dailySiteData;
    private Object logs;

    public boolean isDark() {
        return dark;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
    }

    public String getColorPrimary() {
        return colorPrimary;
    }

    public void setColorPrimary(String colorPrimary) {
        this.colorPrimary = colorPrimary;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public StatisticsConfig getConfig() {
        return config;
    }

    public void setConfig(StatisticsConfig config) {
        this.config = config;
    }

    public StatisticsNotificationChannels getNotificationChannels() {
        return notificationChannels;
    }

    public void setNotificationChannels(StatisticsNotificationChannels notificationChannels) {
        this.notificationChannels = notificationChannels;
    }

    public Object getSummary() {
        return summary;
    }

    public void setSummary(Object summary) {
        this.summary = summary;
    }

    public Object getCharts() {
        return charts;
    }

    public void setCharts(Object charts) {
        this.charts = charts;
    }

    public Object getDailySiteData() {
        return dailySiteData;
    }

    public void setDailySiteData(Object dailySiteData) {
        this.dailySiteData = dailySiteData;
    }

    public Object getLogs() {
        return logs;
    }

    public void setLogs(Object logs) {
        this.logs = logs;
    }
}
