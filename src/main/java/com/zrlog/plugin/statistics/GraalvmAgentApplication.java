package com.zrlog.plugin.statistics;

import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.type.RunType;
import com.zrlog.plugin.statistics.controller.StatisticsController;
import com.zrlog.plugin.common.PluginNativeImageUtils;
import com.zrlog.plugin.statistics.model.StatisticsActionResponse;
import com.zrlog.plugin.statistics.model.StatisticsApiResponse;
import com.zrlog.plugin.statistics.model.StatisticsArticleVisitRequest;
import com.zrlog.plugin.statistics.model.StatisticsConfig;
import com.zrlog.plugin.statistics.model.StatisticsConfigValues;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteData;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteDataStore;
import com.zrlog.plugin.statistics.model.StatisticsLogEntry;
import com.zrlog.plugin.statistics.model.StatisticsLogStore;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannelInfo;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;
import com.zrlog.plugin.statistics.model.StatisticsNotificationSettingValues;
import com.zrlog.plugin.statistics.model.StatisticsPageData;
import com.zrlog.plugin.statistics.model.StatisticsRequestParams;
import com.zrlog.plugin.statistics.model.WebsiteKeyRequest;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class GraalvmAgentApplication {


    public static void main(String[] args) throws IOException {
        RunConstants.runType = RunType.AGENT;
        PluginNativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(WebsiteKeyRequest.class,
                StatisticsActionResponse.class, StatisticsApiResponse.class, StatisticsArticleVisitRequest.class,
                StatisticsConfig.class, StatisticsConfigValues.class,
                StatisticsDailySiteData.class, StatisticsDailySiteDataStore.class,
                StatisticsLogEntry.class, StatisticsLogStore.class,
                StatisticsNotificationChannelInfo.class, StatisticsNotificationChannels.class,
                StatisticsNotificationSettingValues.class, StatisticsPageData.class, StatisticsRequestParams.class));
        String basePath = System.getProperty("user.dir").replace("\\target","").replace("/target", "");
        File file = new File(basePath + "/src/main/resources");
        PluginNativeImageUtils.doLoopResourceLoad(file.listFiles(), file.getPath()  + "/", "/");
        PluginNativeImageUtils.exposeController(Collections.singletonList(StatisticsController.class));
        PluginNativeImageUtils.usedGsonObject();
        Application.main(args);

    }
}
