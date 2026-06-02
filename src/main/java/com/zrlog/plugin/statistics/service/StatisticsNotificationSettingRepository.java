package com.zrlog.plugin.statistics.service;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.common.LoggerUtil;
import com.zrlog.plugin.common.SessionKvRepository;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatisticsNotificationSettingRepository {

    private static final Logger LOGGER = LoggerUtil.getLogger(StatisticsNotificationSettingRepository.class);
    private static final StatisticsNotificationSettingRepository INSTANCE = new StatisticsNotificationSettingRepository();

    public static StatisticsNotificationSettingRepository getInstance() {
        return INSTANCE;
    }

    public StatisticsNotificationChannels get(IOSession session) {
        try {
            Map<String, Object> values = SessionKvRepository.of(session).read(
                    StatisticsNotificationChannels.DAILY_CHANNELS_KEY,
                    StatisticsNotificationChannels.FAILED_CHANNELS_KEY);
            StatisticsNotificationChannels channels = new StatisticsNotificationChannels();
            channels.setDailyChannels(StatisticsNotificationChannels.decodeChannels(
                    stringValue(values.get(StatisticsNotificationChannels.DAILY_CHANNELS_KEY)), null));
            channels.setFailedChannels(StatisticsNotificationChannels.decodeChannels(
                    stringValue(values.get(StatisticsNotificationChannels.FAILED_CHANNELS_KEY)),
                    channels.getDailyChannels()));
            return StatisticsNotificationChannels.normalize(channels);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "read statistics notification channels from website config error", e);
            return StatisticsNotificationChannels.defaults();
        }
    }

    public void save(IOSession session, StatisticsNotificationChannels channels) {
        StatisticsNotificationChannels normalized = StatisticsNotificationChannels.normalize(channels);
        Map<String, String> values = new HashMap<>();
        values.put(StatisticsNotificationChannels.DAILY_CHANNELS_KEY,
                StatisticsNotificationChannels.encodeChannels(normalized.getDailyChannels()));
        values.put(StatisticsNotificationChannels.FAILED_CHANNELS_KEY,
                StatisticsNotificationChannels.encodeChannels(normalized.getFailedChannels()));
        SessionKvRepository.of(session).write(values);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
