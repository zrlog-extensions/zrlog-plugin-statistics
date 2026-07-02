package com.zrlog.plugin.statistics.controller;

import com.google.gson.Gson;
import com.zrlog.plugin.IMsgPacketCallBack;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.common.IdUtil;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.SessionNotificationChannelRepository;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.NotificationChannelProvider;
import com.zrlog.plugin.message.NotificationChannelQueryResult;
import com.zrlog.plugin.statistics.model.StatisticsActionResponse;
import com.zrlog.plugin.statistics.model.StatisticsApiResponse;
import com.zrlog.plugin.statistics.model.StatisticsArticleVisitRequest;
import com.zrlog.plugin.statistics.model.StatisticsConfig;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannelInfo;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;
import com.zrlog.plugin.statistics.model.StatisticsPageData;
import com.zrlog.plugin.statistics.model.StatisticsRequestParams;
import com.zrlog.plugin.statistics.service.StatisticsNotificationSettingRepository;
import com.zrlog.plugin.statistics.service.StatisticsRepository;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugin.type.RunType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class StatisticsController {


    private static final Logger LOGGER = LoggerUtil.getLogger(StatisticsController.class);

    private final IOSession session;
    private final MsgPacket requestPacket;
    private final HttpRequestInfo requestInfo;
    private final StatisticsRepository repository = StatisticsRepository.getInstance();
    private final StatisticsNotificationSettingRepository notificationSettingRepository = StatisticsNotificationSettingRepository.getInstance();
    private final Gson gson = new Gson();
    private static final String SVG_STR = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\">\n" +
            "  <rect width=\"1\" height=\"1\" fill=\"transparent\"/>\n" +
            "</svg>";

    public StatisticsController(IOSession session, MsgPacket requestPacket, HttpRequestInfo requestInfo) {
        this.session = session;
        this.requestPacket = requestPacket;
        this.requestInfo = requestInfo;
    }

    public void update() {
        StatisticsConfig config = repository.saveConfig(session, params());
        response(StatisticsApiResponse.success(config));
    }

    public void index() {
        Map<String, Object> data = new HashMap<>();
        data.put("theme", isDarkMode() ? "dark" : "light");
        data.put("data", gson.toJson(pageData()));
        session.responseHtml("/templates/index", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
    }

    public void json() {
        response(pageData());
    }

    public void list() {
        StatisticsConfig config = repository.readConfig(session);
        response(StatisticsApiResponse.success(repository.page(session, params(), config.getRetentionDays())));
    }

    public void notificationChannels() {
        try {
            response(StatisticsApiResponse.success(notificationChannelInfo()));
        } catch (Exception e) {
            response(StatisticsApiResponse.error(errorMessage(e.getMessage())));
        }
    }

    public void saveNotificationChannels() {
        StatisticsRequestParams params = params();
        List<NotificationChannelProvider> providers;
        try {
            providers = queryNotificationProviders();
        } catch (Exception e) {
            response(StatisticsApiResponse.error(errorMessage(e.getMessage())));
            return;
        }
        Set<String> availableChannels = availableChannels(providers);
        List<String> dailyChannels = configuredChannels(params.getDailyChannels(), availableChannels);
        if (dailyChannels.isEmpty()) {
            response(StatisticsApiResponse.error("请选择 plugin-core 中可用的通知渠道"));
            return;
        }
        List<String> failedChannels = configuredChannels(params.getFailedChannels(), availableChannels);
        if (failedChannels.isEmpty()) {
            failedChannels = dailyChannels;
        }
        StatisticsNotificationChannels channels = new StatisticsNotificationChannels();
        channels.setDailyChannels(dailyChannels);
        channels.setFailedChannels(failedChannels);
        notificationSettingRepository.save(session, channels);
        StatisticsNotificationChannelInfo result = new StatisticsNotificationChannelInfo();
        result.setSettings(notificationSettingRepository.get(session));
        result.setProviders(providers);
        response(StatisticsApiResponse.success(result));
    }

    public void surface() {
        response(StatisticsApiResponse.success(repository.surfaceData(session)));
    }

    public void surfaceAction() {
        response(StatisticsApiResponse.success(new StatisticsActionResponse("已刷新统计", repository.surfaceData(session))));
    }

    public void widget() {
        StatisticsConfig config = repository.readConfig(session);
        Map<String, Object> data = new HashMap<>();
        data.put("host", widgetHost(config.getHost()));
        session.responseHtml("/widget", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
    }

    public void img() {
        String[] path = requestInfo.getParam().get("path");
        if (path == null || path.length == 0) {
            if (RunConstants.runType == RunType.DEV) {
                LOGGER.warning("Request missing path");
            }
            session.responseHtmlStr(SVG_STR, requestPacket.getMethodStr(), requestPacket.getMsgId());
            return;
        }
        String aliasKey = URLDecoder.decode(path[0], StandardCharsets.UTF_8).replace("/", "").replace(".html", "");
        repository.recordVisit(session, requestInfo, aliasKey);
        session.sendMsg(ContentType.JSON, new StatisticsArticleVisitRequest(aliasKey),
                ActionType.ARTICLE_VISIT_COUNT_ADD_ONE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, new IMsgPacketCallBack() {
            @Override
            public void handler(MsgPacket responseMsgPacket) {
                session.sendMsg(new MsgPacket(SVG_STR, ContentType.IMAGE_SVG_XML, MsgPacketStatus.RESPONSE_SUCCESS, requestPacket.getMsgId(), requestPacket.getMethodStr()));
                if (RunConstants.runType == RunType.DEV) {
                    LOGGER.info("Report " + aliasKey + " success.");
                }
            }
        });
    }

    private void response(StatisticsApiResponse<?> response) {
        session.sendMsg(ContentType.JSON, response, requestPacket.getMethodStr(), requestPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
    }

    private StatisticsApiResponse<StatisticsPageData> pageData() {
        StatisticsConfig config = repository.readConfig(session);
        Map<String, Object> overview = repository.overview(session, config.getRetentionDays());
        StatisticsRequestParams firstPageParams = new StatisticsRequestParams();
        firstPageParams.setPage("1");
        firstPageParams.setPageSize("10");
        StatisticsPageData data = new StatisticsPageData();
        data.setDark(requestInfo.isDarkMode());
        data.setColorPrimary(requestInfo.getAdminColorPrimary());
        data.setPlugin(session.getPlugin());
        data.setConfig(config);
        data.setNotificationChannels(notificationSettingRepository.get(session));
        data.setSummary(overview.get("summary"));
        data.setCharts(overview.get("charts"));
        data.setDailySiteData(overview.get("dailySiteData"));
        data.setLogs(repository.page(session, firstPageParams, config.getRetentionDays()));
        return StatisticsApiResponse.success(data);
    }

    private StatisticsNotificationChannelInfo notificationChannelInfo() {
        StatisticsNotificationChannelInfo data = new StatisticsNotificationChannelInfo();
        data.setSettings(notificationSettingRepository.get(session));
        data.setProviders(queryNotificationProviders());
        return data;
    }

    private List<NotificationChannelProvider> queryNotificationProviders() {
        NotificationChannelQueryResult result = SessionNotificationChannelRepository.of(session).query(Duration.ofSeconds(15));
        if (!result.isOk()) {
            throw new IllegalStateException(stringValue(result.getMessage()));
        }
        return result.getItems();
    }

    private Set<String> availableChannels(List<NotificationChannelProvider> providers) {
        Set<String> channels = new LinkedHashSet<>();
        for (NotificationChannelProvider item : providers) {
            String channel = item == null ? "" : item.getChannel();
            if (notBlank(channel)) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private List<String> configuredChannels(Object value, Set<String> availableChannels) {
        List<String> result = new ArrayList<>();
        for (String channel : channelList(value)) {
            if (availableChannels.contains(channel) && !result.contains(channel)) {
                result.add(channel);
            }
        }
        return result;
    }

    private StatisticsRequestParams params() {
        if (requestInfo.getRequestBody() != null && requestInfo.getRequestBody().length > 0) {
            String body = new String(requestInfo.getRequestBody(), StandardCharsets.UTF_8);
            if (body.trim().startsWith("{")) {
                StatisticsRequestParams params = gson.fromJson(body, StatisticsRequestParams.class);
                return params == null ? new StatisticsRequestParams() : params;
            }
        }
        return StatisticsRequestParams.fromParams(this::hasParam, this::paramObject);
    }

    private boolean hasParam(String key) {
        return requestInfo.getParam() != null && requestInfo.getParam().containsKey(key);
    }

    private Object paramObject(String key) {
        if (!hasParam(key)) {
            return null;
        }
        String[] values = requestInfo.getParam().get(key);
        return values.length == 1 ? values[0] : values;
    }

    private boolean isDarkMode() {
        return requestInfo.isDarkMode();
    }

    private String widgetHost(String host) {
        if (!notBlank(host)) {
            return "";
        }
        String trimmed = host.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("//")) {
            return trimmed;
        }
        return "//" + trimmed;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String[]) {
            String[] values = (String[]) value;
            return values.length == 0 ? "" : values[0];
        }
        if (value instanceof List && !((List) value).isEmpty()) {
            return String.valueOf(((List) value).get(0));
        }
        return String.valueOf(value);
    }

    private List<String> channelList(Object value) {
        if (value instanceof String[]) {
            return Arrays.asList((String[]) value);
        }
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List) value) {
                addChannels(result, stringValue(item));
            }
            return result;
        }
        return Arrays.asList(stringValue(value).split(","));
    }

    private void addChannels(List<String> result, String text) {
        if (!notBlank(text)) {
            return;
        }
        String[] values = text.split(",");
        for (String value : values) {
            if (notBlank(value)) {
                result.add(value.trim());
            }
        }
    }

    private String errorMessage(String message) {
        return notBlank(message) ? message : "操作失败";
    }
}
