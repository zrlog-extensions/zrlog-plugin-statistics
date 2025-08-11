package com.zrlog.plugin.statistics;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.client.ClientActionHandler;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;

public class StatisticsClientActionHandler extends ClientActionHandler {

    @Override
    public void httpMethod(IOSession session, MsgPacket msgPacket) {
        HttpRequestInfo httpRequestInfo = new Gson().fromJson(msgPacket.getDataStr(), HttpRequestInfo.class);
        if (httpRequestInfo.getUri().startsWith("/xxxxxxx-statistics")) {
            //这里可以处理一些特殊请求
            super.httpMethod(session, msgPacket);
        } else {
            super.httpMethod(session, msgPacket);
        }
    }

    @Override
    public void refreshCache(IOSession session, MsgPacket msgPacket) {

    }
}
