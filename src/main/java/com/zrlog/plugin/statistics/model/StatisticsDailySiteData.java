package com.zrlog.plugin.statistics.model;

public class StatisticsDailySiteData {

    private String date;
    private int pv;
    private int uv;
    private int sessions;
    private int uniqueIp;
    private int articleCount;
    private String topArticle;
    private int topArticleViews;
    private String topSource;
    private int topSourceViews;
    private int mobile;
    private int tablet;
    private int desktop;
    private int unknownDevice;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getPv() {
        return pv;
    }

    public void setPv(int pv) {
        this.pv = pv;
    }

    public int getUv() {
        return uv;
    }

    public void setUv(int uv) {
        this.uv = uv;
    }

    public int getSessions() {
        return sessions;
    }

    public void setSessions(int sessions) {
        this.sessions = sessions;
    }

    public int getUniqueIp() {
        return uniqueIp;
    }

    public void setUniqueIp(int uniqueIp) {
        this.uniqueIp = uniqueIp;
    }

    public int getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(int articleCount) {
        this.articleCount = articleCount;
    }

    public String getTopArticle() {
        return topArticle;
    }

    public void setTopArticle(String topArticle) {
        this.topArticle = topArticle;
    }

    public int getTopArticleViews() {
        return topArticleViews;
    }

    public void setTopArticleViews(int topArticleViews) {
        this.topArticleViews = topArticleViews;
    }

    public String getTopSource() {
        return topSource;
    }

    public void setTopSource(String topSource) {
        this.topSource = topSource;
    }

    public int getTopSourceViews() {
        return topSourceViews;
    }

    public void setTopSourceViews(int topSourceViews) {
        this.topSourceViews = topSourceViews;
    }

    public int getMobile() {
        return mobile;
    }

    public void setMobile(int mobile) {
        this.mobile = mobile;
    }

    public int getTablet() {
        return tablet;
    }

    public void setTablet(int tablet) {
        this.tablet = tablet;
    }

    public int getDesktop() {
        return desktop;
    }

    public void setDesktop(int desktop) {
        this.desktop = desktop;
    }

    public int getUnknownDevice() {
        return unknownDevice;
    }

    public void setUnknownDevice(int unknownDevice) {
        this.unknownDevice = unknownDevice;
    }
}
