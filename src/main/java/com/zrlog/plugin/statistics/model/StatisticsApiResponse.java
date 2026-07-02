package com.zrlog.plugin.statistics.model;

public class StatisticsApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public StatisticsApiResponse() {
    }

    private StatisticsApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> StatisticsApiResponse<T> success(T data) {
        return new StatisticsApiResponse<T>(true, null, data);
    }

    public static StatisticsApiResponse<Void> error(String message) {
        return new StatisticsApiResponse<Void>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
