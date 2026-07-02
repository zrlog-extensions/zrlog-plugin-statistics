package com.zrlog.plugin.statistics.model;

public class StatisticsActionResponse {

    private String message;
    private Object surface;

    public StatisticsActionResponse() {
    }

    public StatisticsActionResponse(String message, Object surface) {
        this.message = message;
        this.surface = surface;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getSurface() {
        return surface;
    }

    public void setSurface(Object surface) {
        this.surface = surface;
    }
}
