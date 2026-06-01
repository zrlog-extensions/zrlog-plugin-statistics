package com.zrlog.plugin.statistics.service;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.SessionKvRepository;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;

import java.util.logging.Level;
import java.util.logging.Logger;

public class StatisticsNotificationSettingRepository {

    private static final Logger LOGGER = LoggerUtil.getLogger(StatisticsNotificationSettingRepository.class);
    private static final StatisticsNotificationSettingRepository INSTANCE = new StatisticsNotificationSettingRepository();
    private final Gson gson = new Gson();

    public static StatisticsNotificationSettingRepository getInstance() {
        return INSTANCE;
    }

    public StatisticsNotificationChannels get(IOSession session) {
        try {
            String json = SessionKvRepository.of(session).get(StatisticsNotificationChannels.STORE_KEY).orElse("");
            if (!notBlank(json)) {
                return StatisticsNotificationChannels.defaults();
            }
            return StatisticsNotificationChannels.normalize(gson.fromJson(json, StatisticsNotificationChannels.class));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "read statistics notification channels from website config error", e);
            return StatisticsNotificationChannels.defaults();
        }
    }

    public void save(IOSession session, StatisticsNotificationChannels channels) {
        SessionKvRepository.of(session).put(StatisticsNotificationChannels.STORE_KEY,
                gson.toJson(StatisticsNotificationChannels.normalize(channels)));
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
