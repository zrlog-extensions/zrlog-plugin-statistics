package com.zrlog.plugin.statistics;


import com.zrlog.plugin.client.NioClient;
import com.zrlog.plugin.render.SimpleTemplateRender;
import com.zrlog.plugin.statistics.controller.StatisticsController;
import com.zrlog.plugin.statistics.handle.ConnectHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Application {

    private static final ConnectHandler connectHandler = new ConnectHandler();

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        List<Class> classList = new ArrayList<>();
        classList.add(StatisticsController.class);
        new NioClient(connectHandler, new SimpleTemplateRender(), new StatisticsClientActionHandler()).connectServer(args, classList, StatisticsPluginAction.class);
    }
}

