package com.zrlog.plugin.statistics.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StatisticsNotificationChannels {

    public static final String STORE_KEY = "plugin.statistics.notification.channels";
    public static final String SCHEMA = STORE_KEY;
    private static final List<String> FALLBACK_CHANNELS = Arrays.asList("email");

    private String schema = SCHEMA;
    private int version = 1;
    private StatisticsNotificationChannelData data = new StatisticsNotificationChannelData();

    public static StatisticsNotificationChannels defaults() {
        return normalize(new StatisticsNotificationChannels());
    }

    public static StatisticsNotificationChannels normalize(StatisticsNotificationChannels channels) {
        StatisticsNotificationChannels normalized = channels == null ? new StatisticsNotificationChannels() : channels;
        normalized.setSchema(SCHEMA);
        if (normalized.getVersion() <= 0) {
            normalized.setVersion(1);
        }
        StatisticsNotificationChannelData data = normalized.getData();
        if (data == null) {
            data = new StatisticsNotificationChannelData();
            normalized.setData(data);
        }
        data.setDailyChannels(normalizeChannels(data.getDailyChannels(), FALLBACK_CHANNELS));
        data.setFailedChannels(normalizeChannels(data.getFailedChannels(), data.getDailyChannels()));
        return normalized;
    }

    public List<String> dailyChannels() {
        return copy(normalize(this).getData().getDailyChannels());
    }

    public List<String> failedChannels() {
        return copy(normalize(this).getData().getFailedChannels());
    }

    private static List<String> normalizeChannels(List<String> channels, List<String> fallback) {
        List<String> values = new ArrayList<String>();
        if (channels != null) {
            for (String channel : channels) {
                if (channel == null) {
                    continue;
                }
                String text = channel.trim();
                if (!text.isEmpty() && !values.contains(text)) {
                    values.add(text);
                }
            }
        }
        if (values.isEmpty()) {
            values.addAll(fallback == null || fallback.isEmpty() ? FALLBACK_CHANNELS : fallback);
        }
        return values;
    }

    private static List<String> copy(List<String> values) {
        return new ArrayList<String>(values == null || values.isEmpty() ? FALLBACK_CHANNELS : values);
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public StatisticsNotificationChannelData getData() {
        return data;
    }

    public void setData(StatisticsNotificationChannelData data) {
        this.data = data;
    }

    public static class StatisticsNotificationChannelData {
        private List<String> dailyChannels = new ArrayList<String>(FALLBACK_CHANNELS);
        private List<String> failedChannels = new ArrayList<String>(FALLBACK_CHANNELS);

        public List<String> getDailyChannels() {
            return dailyChannels;
        }

        public void setDailyChannels(List<String> dailyChannels) {
            this.dailyChannels = dailyChannels;
        }

        public List<String> getFailedChannels() {
            return failedChannels;
        }

        public void setFailedChannels(List<String> failedChannels) {
            this.failedChannels = failedChannels;
        }
    }
}
