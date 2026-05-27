package com.zrlog.plugin.statistics.service;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.statistics.model.StatisticsLogEntry;
import com.zrlog.plugin.statistics.model.StatisticsLogStore;
import com.zrlog.plugin.type.ActionType;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatisticsRepository {

    private static final Logger LOGGER = LoggerUtil.getLogger(StatisticsRepository.class);
    private static final StatisticsRepository INSTANCE = new StatisticsRepository();
    private static final String STORE_KEY = "statisticsLogs";
    private static final int RETENTION_DAYS = 30;
    private static final int MAX_VALUE_BYTES = 950 * 1024;
    private static final int TOP_LIMIT = 8;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter CHART_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private final Gson gson = new Gson();

    public static StatisticsRepository getInstance() {
        return INSTANCE;
    }

    public synchronized void recordVisit(IOSession session, HttpRequestInfo requestInfo, String alias) {
        try {
            LocalDate today = LocalDate.now(ZONE);
            StatisticsLogStore store = readStore(session);
            store.getItems().add(toEntry(requestInfo, alias));
            removeExpired(store, today);
            writeStore(session, store);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "record statistics visit log error", e);
        }
    }

    public synchronized Map<String, Object> surfaceData(IOSession session) {
        List<StatisticsLogEntry> logs = listRecentLogs(session);
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Integer> dayCounts = initDayCounts(today);
        Map<String, Integer> aliasCounts = new HashMap<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        Set<String> ips = new HashSet<>();
        for (StatisticsLogEntry log : logs) {
            LocalDate day = toDay(log.getTimestamp());
            if (day == null || day.isBefore(today.minusDays(RETENTION_DAYS - 1L)) || day.isAfter(today)) {
                continue;
            }
            String dayKey = DAY_FORMATTER.format(day);
            dayCounts.put(dayKey, dayCounts.getOrDefault(dayKey, 0) + 1);
            String alias = defaultText(log.getAlias(), "未知文章");
            aliasCounts.put(alias, aliasCounts.getOrDefault(alias, 0) + 1);
            String source = sourceName(log.getReferer());
            sourceCounts.put(source, sourceCounts.getOrDefault(source, 0) + 1);
            if (notBlank(log.getIp())) {
                ips.add(log.getIp());
            }
        }

        Map<String, Object> surface = new LinkedHashMap<>();
        surface.put("version", "1.0");
        surface.put("title", "访问统计");
        surface.put("description", "最近 30 天记录 " + logs.size() + " 次访问");
        surface.put("status", logs.isEmpty() ? "normal" : "processing");
        surface.put("view", viewMap("进入插件", "index", "index"));
        surface.put("metrics", metrics(logs.size(), dayCounts.getOrDefault(DAY_FORMATTER.format(today), 0), ips.size(), aliasCounts.size()));
        surface.put("charts", charts(today, dayCounts, aliasCounts, sourceCounts));
        surface.put("items", recentItems(logs));
        return surface;
    }

    private StatisticsLogEntry toEntry(HttpRequestInfo requestInfo, String alias) {
        Map<String, String> header = requestInfo.getHeader() == null ? Collections.emptyMap() : requestInfo.getHeader();
        StatisticsLogEntry entry = new StatisticsLogEntry();
        entry.setTimestamp(System.currentTimeMillis());
        entry.setAlias(limit(alias, 120));
        entry.setPath(limit(firstParam(requestInfo, "path"), 240));
        entry.setUrl(limit(defaultText(requestInfo.getFullUrl(), defaultText(requestInfo.getAccessUrl(), requestInfo.getUri())), 500));
        entry.setReferer(limit(headerValue(header, "Referer", "Referer"), 500));
        entry.setUserAgent(limit(headerValue(header, "User-Agent", "User-Agent"), 500));
        entry.setIp(limit(clientIp(header), 80));
        entry.setMethod(requestInfo.getHttpMethod() == null ? "" : requestInfo.getHttpMethod().name());
        entry.setLanguage(limit(headerValue(header, "Accept-Language", "Accept-Language"), 120));
        return entry;
    }

    private List<StatisticsLogEntry> listRecentLogs(IOSession session) {
        LocalDate today = LocalDate.now(ZONE);
        StatisticsLogStore store = readStore(session);
        removeExpired(store, today);
        List<StatisticsLogEntry> logs = new ArrayList<>(store.getItems());
        logs.sort(Comparator.comparingLong(StatisticsLogEntry::getTimestamp));
        return logs;
    }

    private Map<String, Integer> initDayCounts(LocalDate today) {
        Map<String, Integer> dayCounts = new LinkedHashMap<>();
        for (int i = RETENTION_DAYS - 1; i >= 0; i--) {
            dayCounts.put(DAY_FORMATTER.format(today.minusDays(i)), 0);
        }
        return dayCounts;
    }

    private List<Map<String, Object>> metrics(int total, int today, int uniqueIp, int articles) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(metricMap("30 天访问", total, "normal"));
        metrics.add(metricMap("今日访问", today, today > 0 ? "processing" : "normal"));
        metrics.add(metricMap("访客 IP", uniqueIp, "normal"));
        metrics.add(metricMap("文章数", articles, "normal"));
        return metrics;
    }

    private List<Map<String, Object>> charts(LocalDate today, Map<String, Integer> dayCounts,
                                             Map<String, Integer> aliasCounts, Map<String, Integer> sourceCounts) {
        List<Map<String, Object>> charts = new ArrayList<>();
        List<Map<String, Object>> trendRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dayCounts.entrySet()) {
            LocalDate day = parseDay(entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day == null ? entry.getKey() : CHART_DAY_FORMATTER.format(day));
            row.put("views", entry.getValue());
            trendRows.add(row);
        }
        charts.add(chartMap("line", "最近 30 天访问", "date", "views", null, null, "次", 180, trendRows));

        List<Map<String, Object>> topRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : topEntries(aliasCounts, TOP_LIMIT)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("article", entry.getKey());
            row.put("views", entry.getValue());
            topRows.add(row);
        }
        charts.add(chartMap("bar", "热门文章", "article", "views", null, null, "次", 180, topRows));

        List<Map<String, Object>> sourceRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : topEntries(sourceCounts, TOP_LIMIT)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", entry.getKey());
            row.put("count", entry.getValue());
            sourceRows.add(row);
        }
        charts.add(chartMap("donut", "访问来源", null, null, "source", "count", "次", 180, sourceRows));
        return charts;
    }

    private List<Map<String, Object>> recentItems(List<StatisticsLogEntry> logs) {
        List<Map<String, Object>> items = new ArrayList<>();
        int count = 0;
        for (int i = logs.size() - 1; i >= 0 && count < 5; i--) {
            StatisticsLogEntry log = logs.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(log.getTimestamp()) + "-" + count);
            item.put("title", defaultText(log.getAlias(), "未知文章"));
            item.put("description", formatTime(log.getTimestamp()) + " / " + sourceName(log.getReferer()));
            item.put("status", "normal");
            items.add(item);
            count++;
        }
        return items;
    }

    private Map<String, Object> chartMap(String type, String title, String xField, String yField,
                                         String nameField, String valueField, String unit, int height,
                                         List<Map<String, Object>> data) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", type);
        chart.put("title", title);
        if (xField != null) {
            chart.put("xField", xField);
        }
        if (yField != null) {
            chart.put("yField", yField);
        }
        if (nameField != null) {
            chart.put("nameField", nameField);
        }
        if (valueField != null) {
            chart.put("valueField", valueField);
        }
        chart.put("unit", unit);
        chart.put("height", height);
        chart.put("data", data);
        return chart;
    }

    private List<Map.Entry<String, Integer>> topEntries(Map<String, Integer> counts, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> Integer.compare(right.getValue(), left.getValue()));
        if (entries.size() <= limit) {
            return entries;
        }
        return entries.subList(0, limit);
    }

    private void removeExpired(StatisticsLogStore store, LocalDate today) {
        LocalDate cutoff = today.minusDays(RETENTION_DAYS - 1L);
        store.getItems().removeIf(log -> {
            LocalDate day = toDay(log.getTimestamp());
            return day == null || day.isBefore(cutoff) || day.isAfter(today);
        });
    }

    private StatisticsLogStore readStore(IOSession session) {
        try {
            String json = readWebsiteValue(session, STORE_KEY);
            if (!notBlank(json)) {
                return new StatisticsLogStore();
            }
            StatisticsLogStore store = gson.fromJson(json, StatisticsLogStore.class);
            if (store == null) {
                store = new StatisticsLogStore();
            }
            return store;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "read statistics log store error", e);
            return new StatisticsLogStore();
        }
    }

    private void writeStore(IOSession session, StatisticsLogStore store) {
        String json = gson.toJson(store);
        while (json.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES && !store.getItems().isEmpty()) {
            store.getItems().remove(0);
            json = gson.toJson(store);
        }
        writeWebsiteValue(session, STORE_KEY, json);
    }

    private String readWebsiteValue(IOSession session, String key) {
        Map<String, String> request = new HashMap<>();
        request.put("key", key);
        Map responseMap = session.getResponseSync(ContentType.JSON, request, ActionType.GET_WEBSITE, Map.class);
        if (responseMap == null || responseMap.get(key) == null) {
            return "";
        }
        return String.valueOf(responseMap.get(key));
    }

    private void writeWebsiteValue(IOSession session, String key, String value) {
        Map<String, String> request = new HashMap<>();
        request.put(key, value);
        session.getResponseSync(ContentType.JSON, request, ActionType.SET_WEBSITE, Map.class);
    }

    private Map<String, Object> metricMap(String label, int value, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("value", value);
        map.put("status", status);
        return map;
    }

    private Map<String, Object> viewMap(String label, String view, String url) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("view", view);
        map.put("url", url);
        return map;
    }

    private String sourceName(String referer) {
        if (!notBlank(referer)) {
            return "直接访问";
        }
        String lower = referer.toLowerCase();
        if (lower.contains("google.") || lower.contains("bing.") || lower.contains("baidu.")
                || lower.contains("sogou.") || lower.contains("so.com")) {
            return "搜索引擎";
        }
        return "外部链接";
    }

    private LocalDate toDay(long time) {
        if (time <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(time).atZone(ZONE).toLocalDate();
    }

    private LocalDate parseDay(String value) {
        try {
            return LocalDate.parse(value, DAY_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstParam(HttpRequestInfo requestInfo, String key) {
        if (requestInfo.getParam() == null || requestInfo.getParam().get(key) == null
                || requestInfo.getParam().get(key).length == 0) {
            return "";
        }
        return requestInfo.getParam().get(key)[0];
    }

    private String headerValue(Map<String, String> header, String key, String fallback) {
        String value = header.get(key);
        if (value == null) {
            value = header.get(key.toLowerCase());
        }
        if (value == null) {
            value = header.get(fallback);
        }
        return value == null ? "" : value;
    }

    private String clientIp(Map<String, String> header) {
        String forwarded = headerValue(header, "X-Forwarded-For", "X-Forwarded-For");
        if (notBlank(forwarded)) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = headerValue(header, "X-Real-IP", "X-Real-IP");
        if (notBlank(realIp)) {
            return realIp;
        }
        return headerValue(header, "CF-Connecting-IP", "CF-Connecting-IP");
    }

    private String formatTime(long time) {
        if (time <= 0) {
            return "";
        }
        return new SimpleDateFormat("MM-dd HH:mm").format(new Date(time));
    }

    private String defaultText(String value, String defaultValue) {
        return notBlank(value) ? value : defaultValue;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
