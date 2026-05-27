package com.zrlog.plugin.statistics.model;

import java.util.ArrayList;
import java.util.List;

public class StatisticsLogStore {

    private int version = 1;
    private List<StatisticsLogEntry> items = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<StatisticsLogEntry> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<StatisticsLogEntry> items) {
        this.items = items;
    }
}
