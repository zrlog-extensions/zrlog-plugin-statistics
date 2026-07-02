package com.zrlog.plugin.statistics.model;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class StatisticsRequestParams {

    private String host;
    private String statisticsRetentionDays;
    private String retentionDays;
    private String page;
    private String pageSize;
    private String keyword;
    private String source;
    private String alias;
    private Object dailyChannels;
    private Object failedChannels;

    public static StatisticsRequestParams fromParams(Predicate<String> hasParam, Function<String, Object> paramValue) {
        StatisticsRequestParams request = new StatisticsRequestParams();
        if (hasParam.test("host")) {
            request.setHost(stringValue(paramValue.apply("host")));
        }
        if (hasParam.test("statisticsRetentionDays")) {
            request.setStatisticsRetentionDays(stringValue(paramValue.apply("statisticsRetentionDays")));
        }
        if (hasParam.test("retentionDays")) {
            request.setRetentionDays(stringValue(paramValue.apply("retentionDays")));
        }
        if (hasParam.test("page")) {
            request.setPage(stringValue(paramValue.apply("page")));
        }
        if (hasParam.test("pageSize")) {
            request.setPageSize(stringValue(paramValue.apply("pageSize")));
        }
        if (hasParam.test("keyword")) {
            request.setKeyword(stringValue(paramValue.apply("keyword")));
        }
        if (hasParam.test("source")) {
            request.setSource(stringValue(paramValue.apply("source")));
        }
        if (hasParam.test("alias")) {
            request.setAlias(stringValue(paramValue.apply("alias")));
        }
        if (hasParam.test("dailyChannels")) {
            request.setDailyChannels(paramValue.apply("dailyChannels"));
        }
        if (hasParam.test("failedChannels")) {
            request.setFailedChannels(paramValue.apply("failedChannels"));
        }
        return request;
    }

    private static String stringValue(Object value) {
        if (value instanceof String[]) {
            String[] values = (String[]) value;
            return values.length == 0 ? "" : values[0];
        }
        if (value instanceof List && !((List) value).isEmpty()) {
            return String.valueOf(((List) value).get(0));
        }
        return value == null ? "" : String.valueOf(value);
    }

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

    public String getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(String retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getPageSize() {
        return pageSize;
    }

    public void setPageSize(String pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Object getDailyChannels() {
        return dailyChannels;
    }

    public void setDailyChannels(Object dailyChannels) {
        this.dailyChannels = dailyChannels;
    }

    public Object getFailedChannels() {
        return failedChannels;
    }

    public void setFailedChannels(Object failedChannels) {
        this.failedChannels = failedChannels;
    }
}
