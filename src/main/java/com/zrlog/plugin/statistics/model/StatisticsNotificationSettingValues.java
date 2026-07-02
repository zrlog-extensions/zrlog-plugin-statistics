package com.zrlog.plugin.statistics.model;

public class StatisticsNotificationSettingValues {

    private String notificationDailyChannels;
    private String notificationFailedChannels;

    public String getNotificationDailyChannels() {
        return notificationDailyChannels;
    }

    public void setNotificationDailyChannels(String notificationDailyChannels) {
        this.notificationDailyChannels = notificationDailyChannels;
    }

    public String getNotificationFailedChannels() {
        return notificationFailedChannels;
    }

    public void setNotificationFailedChannels(String notificationFailedChannels) {
        this.notificationFailedChannels = notificationFailedChannels;
    }
}
