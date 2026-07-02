package com.zrlog.plugin.statistics.model;

import com.zrlog.plugin.message.NotificationChannelProvider;

import java.util.List;

public class StatisticsNotificationChannelInfo {

    private StatisticsNotificationChannels settings;
    private List<NotificationChannelProvider> providers;

    public StatisticsNotificationChannels getSettings() {
        return settings;
    }

    public void setSettings(StatisticsNotificationChannels settings) {
        this.settings = settings;
    }

    public List<NotificationChannelProvider> getProviders() {
        return providers;
    }

    public void setProviders(List<NotificationChannelProvider> providers) {
        this.providers = providers;
    }
}
