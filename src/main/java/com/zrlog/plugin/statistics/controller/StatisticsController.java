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
import com.zrlog.plugin.statistics.model.StatisticsConfig;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;
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
import java.util.Objects;
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
        response(successMap(config));
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
        response(successMap(repository.page(session, params(), config.getRetentionDays())));
    }

    public void notificationChannels() {
        try {
            response(successMap(notificationChannelInfo()));
        } catch (Exception e) {
            response(errorMap(e.getMessage()));
        }
    }

    public void saveNotificationChannels() {
        Map<String, Object> params = params();
        List<NotificationChannelProvider> providers;
        try {
            providers = queryNotificationProviders();
        } catch (Exception e) {
            response(errorMap(e.getMessage()));
            return;
        }
        Set<String> availableChannels = availableChannels(providers);
        List<String> dailyChannels = configuredChannels(params.get("dailyChannels"), availableChannels);
        if (dailyChannels.isEmpty()) {
            response(errorMap("请选择 plugin-core 中可用的通知渠道"));
            return;
        }
        List<String> failedChannels = configuredChannels(params.get("failedChannels"), availableChannels);
        if (failedChannels.isEmpty()) {
            failedChannels = dailyChannels;
        }
        StatisticsNotificationChannels channels = new StatisticsNotificationChannels();
        channels.setDailyChannels(dailyChannels);
        channels.setFailedChannels(failedChannels);
        notificationSettingRepository.save(session, channels);
        Map<String, Object> result = new HashMap<>();
        result.put("settings", notificationSettingRepository.get(session));
        result.put("providers", providers);
        response(successMap(result));
    }

    public void surface() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", repository.surfaceData(session));
        response(response);
    }

    public void surfaceAction() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("message", "已刷新统计");
        data.put("surface", repository.surfaceData(session));
        response.put("data", data);
        response(response);
    }

    public void widget() {
        StatisticsConfig config = repository.readConfig(session);
        Map<String, Object> data = new HashMap<>();
        data.put("host", widgetHost(config.getHost()));
        session.responseHtml("/widget", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
    }

    public void img() {
        Map<String, Object> keyMap = new HashMap<>();
        String[] path = requestInfo.getParam().get("path");
        if (path == null || path.length == 0) {
            if (RunConstants.runType == RunType.DEV) {
                LOGGER.warning("Request missing path");
            }
            session.responseHtmlStr(SVG_STR, requestPacket.getMethodStr(), requestPacket.getMsgId());
            return;
        }
        String aliasKey = URLDecoder.decode(path[0], StandardCharsets.UTF_8).replace("/", "").replace(".html", "");
        keyMap.put("alias", aliasKey);
        repository.recordVisit(session, requestInfo, aliasKey);
        session.sendMsg(ContentType.JSON, keyMap, ActionType.ARTICLE_VISIT_COUNT_ADD_ONE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, new IMsgPacketCallBack() {
            @Override
            public void handler(MsgPacket responseMsgPacket) {
                session.sendMsg(new MsgPacket(SVG_STR, ContentType.IMAGE_SVG_XML, MsgPacketStatus.RESPONSE_SUCCESS, requestPacket.getMsgId(), requestPacket.getMethodStr()));
                if (RunConstants.runType == RunType.DEV) {
                    LOGGER.info("Report " + aliasKey + " success.");
                }
            }
        });
    }

    private void response(Map<String, Object> map) {
        session.sendMsg(ContentType.JSON, map, requestPacket.getMethodStr(), requestPacket.getMsgId(), MsgPacketStatus.RESPONSE_SUCCESS);
    }

    private Map<String, Object> pageData() {
        StatisticsConfig config = repository.readConfig(session);
        Map<String, Object> overview = repository.overview(session, config.getRetentionDays());
        Map<String, Object> firstPageParams = new HashMap<>();
        firstPageParams.put("page", "1");
        firstPageParams.put("pageSize", "10");
        Map<String, Object> data = new HashMap<>();
        data.put("dark", requestInfo.isDarkMode());
        data.put("colorPrimary", requestInfo.getAdminColorPrimary());
        data.put("plugin", session.getPlugin());
        data.put("config", config);
        data.put("notificationChannels", notificationSettingRepository.get(session));
        data.put("summary", overview.get("summary"));
        data.put("charts", overview.get("charts"));
        data.put("dailySiteData", overview.get("dailySiteData"));
        data.put("logs", repository.page(session, firstPageParams, config.getRetentionDays()));
        return successMap(data);
    }

    private Map<String, Object> notificationChannelInfo() {
        Map<String, Object> data = new HashMap<>();
        data.put("settings", notificationSettingRepository.get(session));
        data.put("providers", queryNotificationProviders());
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

    private Map<String, Object> params() {
        if (requestInfo.getRequestBody() != null && requestInfo.getRequestBody().length > 0) {
            String body = new String(requestInfo.getRequestBody(), StandardCharsets.UTF_8);
            if (body.trim().startsWith("{")) {
                return gson.fromJson(body, Map.class);
            }
        }
        if (requestInfo.getParam() == null) {
            return new HashMap<>();
        }
        return requestInfo.simpleParam();
    }

    private Map<String, Object> successMap(Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        map.put("data", data);
        return map;
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("message", notBlank(message) ? message : "操作失败");
        return map;
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
        if (value instanceof List && !((List) value).isEmpty()) {
            return String.valueOf(((List) value).get(0));
        }
        return String.valueOf(value);
    }

    private List<String> channelList(Object value) {
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
}
