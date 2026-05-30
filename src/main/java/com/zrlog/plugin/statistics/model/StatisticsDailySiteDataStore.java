package com.zrlog.plugin.statistics.model;

import java.util.ArrayList;
import java.util.List;

public class StatisticsDailySiteDataStore {

    public static final String SCHEMA = "plugin.statistics.dailySiteData";

    private String schema = SCHEMA;
    private int version = 1;
    private String updatedAt;
    private List<StatisticsDailySiteData> items = new ArrayList<>();

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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<StatisticsDailySiteData> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<StatisticsDailySiteData> items) {
        this.items = items;
    }
}
