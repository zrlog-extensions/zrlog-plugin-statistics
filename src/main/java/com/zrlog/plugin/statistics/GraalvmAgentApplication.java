package com.zrlog.plugin.statistics;

import com.google.gson.Gson;
import com.zrlog.plugin.statistics.controller.StatisticsController;
import com.zrlog.plugin.common.PluginNativeImageUtils;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.message.Plugin;
import com.zrlog.plugin.statistics.model.StatisticsConfig;
import com.zrlog.plugin.statistics.model.StatisticsLogEntry;
import com.zrlog.plugin.statistics.model.StatisticsLogStore;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class GraalvmAgentApplication {


    public static void main(String[] args) throws IOException {
        PluginNativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(StatisticsConfig.class, StatisticsLogEntry.class, StatisticsLogStore.class));
        String basePath = System.getProperty("user.dir").replace("\\target","").replace("/target", "");
        File file = new File(basePath + "/src/main/resources");
        PluginNativeImageUtils.doLoopResourceLoad(file.listFiles(), file.getPath()  + "/", "/");
        PluginNativeImageUtils.exposeController(Collections.singletonList(StatisticsController.class));
        PluginNativeImageUtils.usedGsonObject();
        Application.main(args);

    }
}
