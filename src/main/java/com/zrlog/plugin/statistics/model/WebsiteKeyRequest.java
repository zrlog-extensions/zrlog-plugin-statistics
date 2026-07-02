package com.zrlog.plugin.statistics.model;

public class WebsiteKeyRequest {

    private String key;

    public WebsiteKeyRequest() {
    }

    private WebsiteKeyRequest(String key) {
        this.key = key;
    }

    public static WebsiteKeyRequest of(String key) {
        return new WebsiteKeyRequest(key);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
