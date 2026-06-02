package com.zrlog.plugin.statistics.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StatisticsNotificationChannels {

    public static final String DAILY_CHANNELS_KEY = "notificationDailyChannels";
    public static final String FAILED_CHANNELS_KEY = "notificationFailedChannels";
    private static final List<String> FALLBACK_CHANNELS = Arrays.asList("email");

    private List<String> dailyChannels = new ArrayList<String>(FALLBACK_CHANNELS);
    private List<String> failedChannels = new ArrayList<String>(FALLBACK_CHANNELS);

    public static StatisticsNotificationChannels defaults() {
        return normalize(new StatisticsNotificationChannels());
    }

    public static StatisticsNotificationChannels normalize(StatisticsNotificationChannels channels) {
        StatisticsNotificationChannels normalized = channels == null ? new StatisticsNotificationChannels() : channels;
        normalized.setDailyChannels(normalizeChannels(normalized.getDailyChannels(), FALLBACK_CHANNELS));
        normalized.setFailedChannels(normalizeChannels(normalized.getFailedChannels(), normalized.getDailyChannels()));
        return normalized;
    }

    public List<String> dailyChannels() {
        return copy(normalize(this).getDailyChannels());
    }

    public List<String> failedChannels() {
        return copy(normalize(this).getFailedChannels());
    }

    public static List<String> decodeChannels(String text, List<String> fallback) {
        if (text == null || text.trim().isEmpty()) {
            return normalizeChannels(null, fallback);
        }
        return normalizeChannels(Arrays.asList(text.split(",")), fallback);
    }

    public static String encodeChannels(List<String> channels) {
        return String.join(",", normalizeChannels(channels, FALLBACK_CHANNELS));
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
