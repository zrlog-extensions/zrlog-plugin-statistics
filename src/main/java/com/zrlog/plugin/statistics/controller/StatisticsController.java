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
import com.zrlog.plugin.type.ActionType;
import com.zrlog.plugin.type.RunType;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class StatisticsController {


    private static final Logger LOGGER = LoggerUtil.getLogger(StatisticsController.class);

    private final IOSession session;
    private final MsgPacket requestPacket;
    private final HttpRequestInfo requestInfo;

    public StatisticsController(IOSession session, MsgPacket requestPacket, HttpRequestInfo requestInfo) {
        this.session = session;
        this.requestPacket = requestPacket;
        this.requestInfo = requestInfo;
    }

    public void update() {
        session.sendMsg(new MsgPacket(requestInfo.simpleParam(), ContentType.JSON, MsgPacketStatus.SEND_REQUEST, IdUtil.getInt(), ActionType.SET_WEBSITE.name()), msgPacket -> {
            Map<String, Object> map = new HashMap<>();
            map.put("success", true);
            session.sendMsg(new MsgPacket(map, ContentType.JSON, MsgPacketStatus.RESPONSE_SUCCESS, requestPacket.getMsgId(), requestPacket.getMethodStr()));
            //更新缓存，可选
            //session.sendJsonMsg(new HashMap<>(), ActionType.REFRESH_CACHE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST);
        });
    }

    public void index() {
        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put("key", "host");
        session.sendJsonMsg(keyMap, ActionType.GET_WEBSITE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, msgPacket -> {
            Map map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
            Map<String, Object> data = new HashMap<>();
            data.put("theme", Objects.equals(requestInfo.getHeader().get("Dark-Mode"), "true") ? "dark" : "light");
            if (Objects.isNull(map.get("host"))) {
                map.put("host", "");
            }
            data.put("data", new Gson().toJson(map));
            session.responseHtml("/templates/index.html", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
        });
    }

    public void widget() {
        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put("key", "host");
        session.sendJsonMsg(keyMap, ActionType.GET_WEBSITE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, msgPacket -> {
            Map map = new Gson().fromJson(msgPacket.getDataStr(), Map.class);
            if (Objects.isNull(map.get("host"))) {
                map.put("host", "");
            }
            session.responseHtml("/templates/widget.html", map, requestPacket.getMethodStr(), requestPacket.getMsgId());
        });
    }

    public void img() {
        session.sendMsg(new MsgPacket("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\">\n" +
                "  <rect width=\"1\" height=\"1\" fill=\"transparent\"/>\n" +
                "</svg>", ContentType.IMAGE_SVG_XML, MsgPacketStatus.RESPONSE_SUCCESS, requestPacket.getMsgId(), requestPacket.getMethodStr()));
        Map<String, Object> keyMap = new HashMap<>();
        String[] path = requestInfo.getParam().get("path");
        if (path == null || path.length == 0) {
            return;
        }
        String aliasKey = URLDecoder.decode(path[0], Charset.defaultCharset()).replace("/", "").replace(".html", "");
        keyMap.put("alias", aliasKey);
        session.sendMsg(ContentType.JSON, keyMap, ActionType.ARTICLE_VISIT_COUNT_ADD_ONE.name(), IdUtil.getInt(), MsgPacketStatus.SEND_REQUEST, new IMsgPacketCallBack() {
            @Override
            public void handler(MsgPacket responseMsgPacket) {
                if (RunConstants.runType == RunType.DEV) {
                    LOGGER.info("Report " + aliasKey + " success.");
                }
            }
        });
    }
}