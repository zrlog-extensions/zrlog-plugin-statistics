package com.zrlog.plugin.statistics.controller;

import com.google.gson.Gson;
import com.zrlog.plugin.IMsgPacketCallBack;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.common.IdUtil;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.statistics.model.StatisticsConfig;
import com.zrlog.plugin.statistics.service.StatisticsRepository;
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugin.type.RunType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class StatisticsController {


    private static final Logger LOGGER = LoggerUtil.getLogger(StatisticsController.class);

    private final IOSession session;
    private final MsgPacket requestPacket;
    private final HttpRequestInfo requestInfo;
    private final StatisticsRepository repository = StatisticsRepository.getInstance();
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
        data.put("summary", overview.get("summary"));
        data.put("charts", overview.get("charts"));
        data.put("logs", repository.page(session, firstPageParams, config.getRetentionDays()));
        return successMap(data);
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
}
