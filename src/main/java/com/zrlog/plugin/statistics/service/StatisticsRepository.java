package com.zrlog.plugin.statistics.service;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.SessionKvRepository;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.statistics.model.StatisticsConfig;
import com.zrlog.plugin.statistics.model.StatisticsConfigValues;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteData;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteDataStore;
import com.zrlog.plugin.statistics.model.StatisticsLogEntry;
import com.zrlog.plugin.statistics.model.StatisticsLogStore;
import com.zrlog.plugin.statistics.model.StatisticsRequestParams;
import com.zrlog.plugin.statistics.model.WebsiteKeyRequest;
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
    private static final String DAILY_SITE_DATA_KEY = StatisticsDailySiteDataStore.SCHEMA;
    private static final String CONFIG_HOST_KEY = "host";
    private static final String CONFIG_RETENTION_DAYS_KEY = "statisticsRetentionDays";
    private static final String CONFIG_KEYS = CONFIG_HOST_KEY + "," + CONFIG_RETENTION_DAYS_KEY;
    private static final String VISITOR_COOKIE = "zrlog_statistics_vid";
    private static final String SESSION_COOKIE = "zrlog_statistics_sid";
    private static final int DEFAULT_RETENTION_DAYS = 30;
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
            StatisticsLogStore store = readStore(session);
            store.getItems().add(toEntry(requestInfo, alias));
            writeStore(session, store);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "record statistics visit log error", e);
        }
    }

    public synchronized Map<String, Object> surfaceData(IOSession session) {
        StatisticsConfig config = readConfig(session);
        List<StatisticsLogEntry> logs = listRecentLogs(session, config.getRetentionDays());
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Integer> dayCounts = initDayCounts(today, config.getRetentionDays());
        Map<String, Integer> aliasCounts = new HashMap<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        Map<String, Integer> deviceCounts = new HashMap<>();
        Map<String, Integer> viewportCounts = new HashMap<>();
        Map<String, Set<String>> dayVisitors = initDaySets(today, config.getRetentionDays());
        Map<String, Set<String>> daySessions = initDaySets(today, config.getRetentionDays());
        Set<String> ips = new HashSet<>();
        Set<String> visitors = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        for (StatisticsLogEntry log : logs) {
            LocalDate day = toDay(log.getTimestamp());
            if (day == null || day.isBefore(today.minusDays(config.getRetentionDays() - 1L)) || day.isAfter(today)) {
                continue;
            }
            String dayKey = DAY_FORMATTER.format(day);
            dayCounts.put(dayKey, dayCounts.getOrDefault(dayKey, 0) + 1);
            String alias = defaultText(log.getAlias(), "未知文章");
            aliasCounts.put(alias, aliasCounts.getOrDefault(alias, 0) + 1);
            String source = sourceName(log.getReferer());
            sourceCounts.put(source, sourceCounts.getOrDefault(source, 0) + 1);
            String deviceType = deviceType(log.getWindowWidth(), log.getUserAgent());
            deviceCounts.put(deviceType, deviceCounts.getOrDefault(deviceType, 0) + 1);
            String viewportRange = viewportRange(log.getWindowWidth());
            viewportCounts.put(viewportRange, viewportCounts.getOrDefault(viewportRange, 0) + 1);
            if (notBlank(log.getIp())) {
                ips.add(log.getIp());
            }
            String visitorId = defaultVisitorId(log);
            String sessionId = defaultSessionId(log);
            visitors.add(visitorId);
            sessions.add(sessionId);
            dayVisitors.get(dayKey).add(visitorId);
            daySessions.get(dayKey).add(sessionId);
        }

        List<StatisticsDailySiteData> dailySiteData = dailySiteData(session, config.getRetentionDays());
        StatisticsDailySiteData todayData = dailySiteData.isEmpty() ? emptyDaily(DAY_FORMATTER.format(today)) : dailySiteData.get(0);
        Map<String, Object> surface = new LinkedHashMap<>();
        surface.put("version", "1.0");
        surface.put("title", "访问统计");
        surface.put("description", "今日 PV " + todayData.getPv() + " / UV " + todayData.getUv()
                + "，最近 " + config.getRetentionDays() + " 天记录 " + logs.size() + " 次访问");
        surface.put("status", logs.isEmpty() ? "normal" : "processing");
        surface.put("view", viewMap("进入插件", "index", "index"));
        surface.put("metrics", metrics(config.getRetentionDays(), logs.size(), visitors.size(), sessions.size(), ips.size(), aliasCounts.size()));
        surface.put("charts", charts(config.getRetentionDays(), today, dayCounts, dayVisitors, daySessions, aliasCounts, sourceCounts, deviceCounts, viewportCounts));
        surface.put("items", dailyItems(dailySiteData));
        return surface;
    }

    public synchronized StatisticsConfig readConfig(IOSession session) {
        StatisticsConfigValues response = session.getResponseSync(ContentType.JSON, WebsiteKeyRequest.of(CONFIG_KEYS),
                ActionType.GET_WEBSITE, StatisticsConfigValues.class);
        if (response == null) {
            response = new StatisticsConfigValues();
        }
        StatisticsConfig config = new StatisticsConfig();
        config.setHost(defaultText(response.getHost(), ""));
        config.setRetentionDays(normalizeRetentionDays(response.getStatisticsRetentionDays()));
        return config;
    }

    public synchronized StatisticsConfig saveConfig(IOSession session, StatisticsRequestParams params) {
        if (params == null) {
            params = new StatisticsRequestParams();
        }
        StatisticsConfig config = new StatisticsConfig();
        config.setHost(limit(defaultText(params.getHost(), ""), 180));
        String retentionDays = defaultText(params.getStatisticsRetentionDays(), "");
        if (!notBlank(retentionDays)) {
            retentionDays = defaultText(params.getRetentionDays(), "");
        }
        config.setRetentionDays(normalizeRetentionDays(retentionDays));
        Map<String, String> request = new HashMap<>();
        request.put(CONFIG_HOST_KEY, config.getHost());
        request.put(CONFIG_RETENTION_DAYS_KEY, String.valueOf(config.getRetentionDays()));
        SessionKvRepository.of(session).write(request);
        return config;
    }

    public synchronized StatisticsDailySiteDataStore refreshDailySiteData(IOSession session) {
        StatisticsConfig config = readConfig(session);
        List<StatisticsLogEntry> logs = listRecentLogs(session, config.getRetentionDays());
        List<StatisticsDailySiteData> liveRows = dailySiteDataFromLogs(logs, config.getRetentionDays());
        StatisticsDailySiteDataStore store = new StatisticsDailySiteDataStore();
        store.setUpdatedAt(Instant.now().toString());
        store.setItems(mergeDailySiteData(readDailySiteDataStore(session).getItems(), liveRows, config.getRetentionDays()));
        SessionKvRepository.of(session).put(DAILY_SITE_DATA_KEY, gson.toJson(store));
        return store;
    }

    public synchronized List<StatisticsDailySiteData> dailySiteData(IOSession session, int retentionDays) {
        List<StatisticsLogEntry> logs = listRecentLogs(session, retentionDays);
        List<StatisticsDailySiteData> liveRows = dailySiteDataFromLogs(logs, retentionDays);
        return mergeDailySiteData(readDailySiteDataStore(session).getItems(), liveRows, retentionDays);
    }

    public synchronized Map<String, Object> overview(IOSession session, int retentionDays) {
        List<StatisticsLogEntry> logs = listRecentLogs(session, retentionDays);
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Integer> dayCounts = initDayCounts(today, retentionDays);
        Map<String, Integer> aliasCounts = new HashMap<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        Map<String, Integer> deviceCounts = new HashMap<>();
        Map<String, Integer> viewportCounts = new HashMap<>();
        Map<String, Set<String>> dayVisitors = initDaySets(today, retentionDays);
        Map<String, Set<String>> daySessions = initDaySets(today, retentionDays);
        Set<String> ips = new HashSet<>();
        Set<String> visitors = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        for (StatisticsLogEntry log : logs) {
            LocalDate day = toDay(log.getTimestamp());
            if (day == null || day.isBefore(today.minusDays(retentionDays - 1L)) || day.isAfter(today)) {
                continue;
            }
            String dayKey = DAY_FORMATTER.format(day);
            dayCounts.put(dayKey, dayCounts.getOrDefault(dayKey, 0) + 1);
            String alias = defaultText(log.getAlias(), "未知文章");
            aliasCounts.put(alias, aliasCounts.getOrDefault(alias, 0) + 1);
            String source = sourceName(log.getReferer());
            sourceCounts.put(source, sourceCounts.getOrDefault(source, 0) + 1);
            String deviceType = deviceType(log.getWindowWidth(), log.getUserAgent());
            deviceCounts.put(deviceType, deviceCounts.getOrDefault(deviceType, 0) + 1);
            String viewportRange = viewportRange(log.getWindowWidth());
            viewportCounts.put(viewportRange, viewportCounts.getOrDefault(viewportRange, 0) + 1);
            if (notBlank(log.getIp())) {
                ips.add(log.getIp());
            }
            String visitorId = defaultVisitorId(log);
            String sessionId = defaultSessionId(log);
            visitors.add(visitorId);
            sessions.add(sessionId);
            dayVisitors.get(dayKey).add(visitorId);
            daySessions.get(dayKey).add(sessionId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", metrics(retentionDays, logs.size(), visitors.size(), sessions.size(), ips.size(), aliasCounts.size()));
        data.put("charts", charts(retentionDays, today, dayCounts, dayVisitors, daySessions, aliasCounts, sourceCounts, deviceCounts, viewportCounts));
        data.put("dailySiteData", dailySiteData(session, retentionDays));
        return data;
    }

    public synchronized Map<String, Object> page(IOSession session, StatisticsRequestParams params, int retentionDays) {
        if (params == null) {
            params = new StatisticsRequestParams();
        }
        int page = Math.max(1, parseInt(defaultText(params.getPage(), ""), 1));
        int pageSize = Math.max(1, Math.min(100, parseInt(defaultText(params.getPageSize(), ""), 10)));
        String keyword = defaultText(params.getKeyword(), "").toLowerCase();
        String source = defaultText(params.getSource(), "");
        String alias = defaultText(params.getAlias(), "").toLowerCase();
        List<StatisticsLogEntry> logs = listRecentLogs(session, retentionDays);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (int i = logs.size() - 1; i >= 0; i--) {
            StatisticsLogEntry log = logs.get(i);
            if (notBlank(source) && !Objects.equals(source, sourceName(log.getReferer()))) {
                continue;
            }
            if (notBlank(alias) && !defaultText(log.getAlias(), "").toLowerCase().contains(alias)) {
                continue;
            }
            Map<String, Object> row = rowMap(log);
            if (notBlank(keyword) && !gson.toJson(row).toLowerCase().contains(keyword)) {
                continue;
            }
            filtered.add(row);
        }
        int total = filtered.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", filtered.subList(from, to));
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
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
        Map<String, String> cookies = cookies(header);
        entry.setVisitorId(limit(firstNotBlank(cookies.get(VISITOR_COOKIE), firstParam(requestInfo, "visitorId")), 80));
        entry.setSessionId(limit(firstNotBlank(cookies.get(SESSION_COOKIE), firstParam(requestInfo, "sessionId")), 80));
        entry.setScreenWidth(parseInt(firstParam(requestInfo, "screenWidth"), 0));
        entry.setScreenHeight(parseInt(firstParam(requestInfo, "screenHeight"), 0));
        entry.setWindowWidth(parseInt(firstParam(requestInfo, "windowWidth"), 0));
        entry.setDevicePixelRatio(parseDouble(firstParam(requestInfo, "devicePixelRatio"), 0));
        return entry;
    }

    private List<StatisticsLogEntry> listRecentLogs(IOSession session, int retentionDays) {
        LocalDate today = LocalDate.now(ZONE);
        StatisticsLogStore store = readStore(session);
        List<StatisticsLogEntry> logs = new ArrayList<>(store.getItems());
        logs.removeIf(log -> {
            LocalDate day = toDay(log.getTimestamp());
            return day == null || day.isBefore(today.minusDays(retentionDays - 1L)) || day.isAfter(today);
        });
        logs.sort(Comparator.comparingLong(StatisticsLogEntry::getTimestamp));
        return logs;
    }

    private Map<String, Integer> initDayCounts(LocalDate today, int retentionDays) {
        Map<String, Integer> dayCounts = new LinkedHashMap<>();
        for (int i = retentionDays - 1; i >= 0; i--) {
            dayCounts.put(DAY_FORMATTER.format(today.minusDays(i)), 0);
        }
        return dayCounts;
    }

    private Map<String, Set<String>> initDaySets(LocalDate today, int retentionDays) {
        Map<String, Set<String>> daySets = new LinkedHashMap<>();
        for (int i = retentionDays - 1; i >= 0; i--) {
            daySets.put(DAY_FORMATTER.format(today.minusDays(i)), new HashSet<>());
        }
        return daySets;
    }

    private List<StatisticsDailySiteData> dailySiteDataFromLogs(List<StatisticsLogEntry> logs, int retentionDays) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate earliest = today.minusDays(retentionDays - 1L);
        Map<String, DailyAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (StatisticsLogEntry log : logs) {
            LocalDate day = toDay(log.getTimestamp());
            if (day == null || day.isBefore(earliest) || day.isAfter(today)) {
                continue;
            }
            String date = DAY_FORMATTER.format(day);
            DailyAccumulator accumulator = accumulatorMap.computeIfAbsent(date, DailyAccumulator::new);
            accumulator.pv++;
            if (notBlank(log.getIp())) {
                accumulator.ips.add(log.getIp());
            }
            accumulator.visitors.add(defaultVisitorId(log));
            accumulator.sessions.add(defaultSessionId(log));
            increase(accumulator.articleCounts, defaultText(log.getAlias(), "未知文章"));
            increase(accumulator.sourceCounts, sourceName(log.getReferer()));
            String device = deviceType(log.getWindowWidth(), log.getUserAgent());
            if ("移动端".equals(device)) {
                accumulator.mobile++;
            } else if ("平板 / 中等屏".equals(device)) {
                accumulator.tablet++;
            } else if ("桌面端".equals(device)) {
                accumulator.desktop++;
            } else {
                accumulator.unknownDevice++;
            }
        }
        List<StatisticsDailySiteData> rows = new ArrayList<>();
        for (DailyAccumulator accumulator : accumulatorMap.values()) {
            rows.add(toDailySiteData(accumulator));
        }
        rows.sort((left, right) -> right.getDate().compareTo(left.getDate()));
        return rows;
    }

    private List<StatisticsDailySiteData> mergeDailySiteData(List<StatisticsDailySiteData> storedRows,
                                                            List<StatisticsDailySiteData> liveRows,
                                                            int retentionDays) {
        Map<String, StatisticsDailySiteData> rowMap = new HashMap<>();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate earliest = today.minusDays(retentionDays - 1L);
        for (StatisticsDailySiteData row : storedRows) {
            LocalDate day = parseDay(row.getDate());
            if (day != null && !day.isBefore(earliest) && !day.isAfter(today)) {
                rowMap.put(row.getDate(), row);
            }
        }
        for (StatisticsDailySiteData row : liveRows) {
            LocalDate day = parseDay(row.getDate());
            if (day != null && !day.isBefore(earliest) && !day.isAfter(today)) {
                rowMap.put(row.getDate(), row);
            }
        }
        List<StatisticsDailySiteData> rows = new ArrayList<>();
        for (int i = 0; i < retentionDays; i++) {
            String date = DAY_FORMATTER.format(today.minusDays(i));
            rows.add(rowMap.getOrDefault(date, emptyDaily(date)));
        }
        return rows;
    }

    private StatisticsDailySiteData toDailySiteData(DailyAccumulator accumulator) {
        StatisticsDailySiteData row = new StatisticsDailySiteData();
        row.setDate(accumulator.date);
        row.setPv(accumulator.pv);
        row.setUv(accumulator.visitors.size());
        row.setSessions(accumulator.sessions.size());
        row.setUniqueIp(accumulator.ips.size());
        row.setArticleCount(accumulator.articleCounts.size());
        Map.Entry<String, Integer> topArticle = topEntry(accumulator.articleCounts);
        if (topArticle != null) {
            row.setTopArticle(topArticle.getKey());
            row.setTopArticleViews(topArticle.getValue());
        }
        Map.Entry<String, Integer> topSource = topEntry(accumulator.sourceCounts);
        if (topSource != null) {
            row.setTopSource(topSource.getKey());
            row.setTopSourceViews(topSource.getValue());
        }
        row.setMobile(accumulator.mobile);
        row.setTablet(accumulator.tablet);
        row.setDesktop(accumulator.desktop);
        row.setUnknownDevice(accumulator.unknownDevice);
        return row;
    }

    private StatisticsDailySiteData emptyDaily(String date) {
        StatisticsDailySiteData row = new StatisticsDailySiteData();
        row.setDate(date);
        row.setTopArticle("");
        row.setTopSource("");
        return row;
    }

    private List<Map<String, Object>> dailyItems(List<StatisticsDailySiteData> dailySiteData) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < dailySiteData.size() && i < 5; i++) {
            StatisticsDailySiteData row = dailySiteData.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getDate());
            item.put("title", row.getDate() + " 站点数据");
            item.put("description", "PV " + row.getPv() + " / UV " + row.getUv() + " / Session " + row.getSessions());
            item.put("status", row.getPv() > 0 ? "processing" : "normal");
            items.add(item);
        }
        return items;
    }

    private Map.Entry<String, Integer> topEntry(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = topEntries(counts, 1);
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(0);
    }

    private void increase(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private List<Map<String, Object>> metrics(int retentionDays, int pv, int uv, int sessionCount, int uniqueIp, int articles) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        metrics.add(metricMap(retentionDays + " 天 PV", pv, "normal"));
        metrics.add(metricMap(retentionDays + " 天 UV", uv, uv > 0 ? "processing" : "normal"));
        metrics.add(metricMap("Session", sessionCount, sessionCount > 0 ? "processing" : "normal"));
        metrics.add(metricMap("访客 IP", uniqueIp, "normal"));
        metrics.add(metricMap("文章数", articles, "normal"));
        return metrics;
    }

    private List<Map<String, Object>> charts(int retentionDays, LocalDate today, Map<String, Integer> dayCounts,
                                             Map<String, Set<String>> dayVisitors, Map<String, Set<String>> daySessions,
                                             Map<String, Integer> aliasCounts, Map<String, Integer> sourceCounts,
                                             Map<String, Integer> deviceCounts, Map<String, Integer> viewportCounts) {
        List<Map<String, Object>> charts = new ArrayList<>();
        List<Map<String, Object>> trendRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dayCounts.entrySet()) {
            LocalDate day = parseDay(entry.getKey());
            String date = day == null ? entry.getKey() : CHART_DAY_FORMATTER.format(day);
            trendRows.add(trendRow(date, "PV", entry.getValue()));
            trendRows.add(trendRow(date, "UV", dayVisitors.getOrDefault(entry.getKey(), Collections.emptySet()).size()));
            trendRows.add(trendRow(date, "Session", daySessions.getOrDefault(entry.getKey(), Collections.emptySet()).size()));
        }
        charts.add(chartMap("line", "最近 " + retentionDays + " 天访问趋势", "date", "value", "metric", null, "次", 180, trendRows));

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

        List<Map<String, Object>> deviceRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : topEntries(deviceCounts, TOP_LIMIT)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device", entry.getKey());
            row.put("count", entry.getValue());
            deviceRows.add(row);
        }
        charts.add(chartMap("donut", "设备分布", null, null, "device", "count", "次", 180, deviceRows));

        List<Map<String, Object>> viewportRows = new ArrayList<>();
        for (String range : viewportRanges()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("range", range);
            row.put("count", viewportCounts.getOrDefault(range, 0));
            viewportRows.add(row);
        }
        charts.add(chartMap("bar", "视口宽度分布", "range", "count", null, null, "次", 180, viewportRows));
        return charts;
    }

    private Map<String, Object> rowMap(StatisticsLogEntry log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(log.getTimestamp()) + "-" + defaultText(log.getAlias(), ""));
        row.put("timestamp", log.getTimestamp());
        row.put("time", formatTime(log.getTimestamp()));
        row.put("alias", defaultText(log.getAlias(), "未知文章"));
        row.put("path", defaultText(log.getPath(), ""));
        row.put("url", defaultText(log.getUrl(), ""));
        row.put("referer", defaultText(log.getReferer(), ""));
        row.put("source", sourceName(log.getReferer()));
        row.put("userAgent", defaultText(log.getUserAgent(), ""));
        row.put("ip", defaultText(log.getIp(), ""));
        row.put("method", defaultText(log.getMethod(), ""));
        row.put("language", defaultText(log.getLanguage(), ""));
        row.put("visitorId", defaultText(log.getVisitorId(), ""));
        row.put("sessionId", defaultText(log.getSessionId(), ""));
        row.put("screenWidth", log.getScreenWidth());
        row.put("screenHeight", log.getScreenHeight());
        row.put("windowWidth", log.getWindowWidth());
        row.put("devicePixelRatio", log.getDevicePixelRatio());
        row.put("deviceType", deviceType(log.getWindowWidth(), log.getUserAgent()));
        row.put("viewportRange", viewportRange(log.getWindowWidth()));
        row.put("browser", browserName(log.getUserAgent()));
        row.put("os", osName(log.getUserAgent()));
        return row;
    }

    private Map<String, Object> trendRow(String date, String metric, int value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", date);
        row.put("metric", metric);
        row.put("value", value);
        return row;
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
        entries.sort((left, right) -> {
            int valueCompare = Integer.compare(right.getValue(), left.getValue());
            if (valueCompare != 0) {
                return valueCompare;
            }
            return left.getKey().compareTo(right.getKey());
        });
        if (entries.size() <= limit) {
            return entries;
        }
        return entries.subList(0, limit);
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

    private StatisticsDailySiteDataStore readDailySiteDataStore(IOSession session) {
        try {
            String json = readWebsiteValue(session, DAILY_SITE_DATA_KEY);
            if (!notBlank(json)) {
                return new StatisticsDailySiteDataStore();
            }
            StatisticsDailySiteDataStore store = gson.fromJson(json, StatisticsDailySiteDataStore.class);
            if (store == null) {
                store = new StatisticsDailySiteDataStore();
            }
            return store;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "read statistics daily site data store error", e);
            return new StatisticsDailySiteDataStore();
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
        return SessionKvRepository.of(session).get(key).orElse("");
    }

    private int normalizeRetentionDays(String value) {
        int days = parseInt(value, DEFAULT_RETENTION_DAYS);
        if (days == 90 || days == 180) {
            return days;
        }
        return DEFAULT_RETENTION_DAYS;
    }

    private int parseInt(String value, int defaultValue) {
        try {
            if (!notBlank(value)) {
                return defaultValue;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseDouble(String value, double defaultValue) {
        try {
            if (!notBlank(value)) {
                return defaultValue;
            }
            return Double.parseDouble(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List && !((List) value).isEmpty()) {
            return String.valueOf(((List) value).get(0));
        }
        return String.valueOf(value);
    }

    private void writeWebsiteValue(IOSession session, String key, String value) {
        SessionKvRepository.of(session).put(key, value);
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

    private String deviceType(int windowWidth, String userAgent) {
        String lower = userAgent == null ? "" : userAgent.toLowerCase();
        if (windowWidth > 0) {
            if (windowWidth < 768) {
                return "移动端";
            }
            if (windowWidth < 1200) {
                return "平板 / 中等屏";
            }
            return "桌面端";
        }
        if (lower.contains("mobile") || lower.contains("iphone") || lower.contains("android")) {
            return "移动端";
        }
        if (lower.contains("ipad") || lower.contains("tablet")) {
            return "平板 / 中等屏";
        }
        return "未知设备";
    }

    private String viewportRange(int windowWidth) {
        if (windowWidth <= 0) {
            return "未知";
        }
        if (windowWidth < 390) {
            return "< 390";
        }
        if (windowWidth < 768) {
            return "390 - 767";
        }
        if (windowWidth < 1200) {
            return "768 - 1199";
        }
        if (windowWidth < 1600) {
            return "1200 - 1599";
        }
        return ">= 1600";
    }

    private List<String> viewportRanges() {
        List<String> ranges = new ArrayList<>();
        ranges.add("< 390");
        ranges.add("390 - 767");
        ranges.add("768 - 1199");
        ranges.add("1200 - 1599");
        ranges.add(">= 1600");
        ranges.add("未知");
        return ranges;
    }

    private String browserName(String userAgent) {
        String lower = userAgent == null ? "" : userAgent.toLowerCase();
        if (lower.contains("edg/")) {
            return "Edge";
        }
        if (lower.contains("firefox/")) {
            return "Firefox";
        }
        if (lower.contains("chrome/") || lower.contains("crios/")) {
            return "Chrome";
        }
        if (lower.contains("safari/")) {
            return "Safari";
        }
        return "其他";
    }

    private String osName(String userAgent) {
        String lower = userAgent == null ? "" : userAgent.toLowerCase();
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            return "iOS";
        }
        if (lower.contains("android")) {
            return "Android";
        }
        if (lower.contains("windows")) {
            return "Windows";
        }
        if (lower.contains("mac os") || lower.contains("macintosh")) {
            return "macOS";
        }
        if (lower.contains("linux")) {
            return "Linux";
        }
        return "其他";
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

    String clientIp(Map<String, String> header) {
        String realIp = headerValue(header, "X-Real-IP", "X-Real-IP");
        if (notBlank(realIp)) {
            return firstIp(realIp);
        }
        String forwarded = headerValue(header, "X-Forwarded-For", "X-Forwarded-For");
        if (notBlank(forwarded)) {
            return firstIp(forwarded);
        }
        return firstIp(headerValue(header, "CF-Connecting-IP", "CF-Connecting-IP"));
    }

    private String firstIp(String value) {
        if (value == null) {
            return "";
        }
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
    }

    private Map<String, String> cookies(Map<String, String> header) {
        String cookieHeader = headerValue(header, "Cookie", "Cookie");
        Map<String, String> cookies = new HashMap<>();
        if (!notBlank(cookieHeader)) {
            return cookies;
        }
        String[] pairs = cookieHeader.split(";");
        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            cookies.put(pair.substring(0, index).trim(), pair.substring(index + 1).trim());
        }
        return cookies;
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

    private String firstNotBlank(String left, String right) {
        return notBlank(left) ? left : right;
    }

    private String defaultVisitorId(StatisticsLogEntry log) {
        if (notBlank(log.getVisitorId())) {
            return log.getVisitorId();
        }
        return "legacy-ip:" + defaultText(log.getIp(), "unknown");
    }

    private String defaultSessionId(StatisticsLogEntry log) {
        if (notBlank(log.getSessionId())) {
            return log.getSessionId();
        }
        return "legacy-session:" + log.getTimestamp() + ":" + defaultText(log.getIp(), "unknown");
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

    private static class DailyAccumulator {
        private final String date;
        private int pv;
        private int mobile;
        private int tablet;
        private int desktop;
        private int unknownDevice;
        private final Set<String> visitors = new HashSet<>();
        private final Set<String> sessions = new HashSet<>();
        private final Set<String> ips = new HashSet<>();
        private final Map<String, Integer> articleCounts = new HashMap<>();
        private final Map<String, Integer> sourceCounts = new HashMap<>();

        private DailyAccumulator(String date) {
            this.date = date;
        }
    }
}
