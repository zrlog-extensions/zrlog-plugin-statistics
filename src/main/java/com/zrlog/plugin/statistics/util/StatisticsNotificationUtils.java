package com.zrlog.plugin.statistics.util;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.NotificationRequest;
import com.zrlog.plugin.render.SimpleTemplateRender;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteData;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteDataStore;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StatisticsNotificationUtils {

    private static final Duration NOTIFICATION_TIMEOUT = Duration.ofSeconds(60);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final SimpleTemplateRender TEMPLATE_RENDER = new SimpleTemplateRender();

    private StatisticsNotificationUtils() {
    }

    public static void publishDailyReport(IOSession session,
                                          StatisticsDailySiteDataStore store,
                                          StatisticsNotificationChannels channels) {
        NotificationRequest request = createDailyReportRequest(session, store, channels);
        publish(session, request);
    }

    public static void publishDailyReportFailure(IOSession session,
                                                 String errorMessage,
                                                 StatisticsNotificationChannels channels) {
        NotificationRequest request = new NotificationRequest();
        request.setSourcePluginId(session.getPlugin().getId());
        request.setSourcePluginName(session.getPlugin().getShortName());
        request.setSourceCapabilityKey("statistics.dailyReport");
        request.setEventType("statistics.daily_report.failed");
        request.setNotificationType("statistics");
        request.setChannels(StatisticsNotificationChannels.normalize(channels).failedChannels());
        request.setTitle("[统计日报] 生成失败");
        request.setContent(TEMPLATE_RENDER.render("/notification/statistics-daily-report-failure",
                session.getPlugin(), failureTemplateData(errorMessage)));
        request.setLevel("warning");
        request.setRequestId(UUID.randomUUID().toString());
        request.setPayload(failurePayload(errorMessage));
        publish(session, request);
    }

    private static NotificationRequest createDailyReportRequest(IOSession session,
                                                                StatisticsDailySiteDataStore store,
                                                                StatisticsNotificationChannels channels) {
        StatisticsDailySiteData row = reportRow(store);
        NotificationRequest request = new NotificationRequest();
        request.setSourcePluginId(session.getPlugin().getId());
        request.setSourcePluginName(session.getPlugin().getShortName());
        request.setSourceCapabilityKey("statistics.dailyReport");
        request.setEventType("statistics.daily_report.generated");
        request.setNotificationType("statistics");
        request.setChannels(StatisticsNotificationChannels.normalize(channels).dailyChannels());
        request.setTitle("[统计日报] " + row.getDate() + " PV " + row.getPv() + " / UV " + row.getUv());
        request.setContent(TEMPLATE_RENDER.render("/notification/statistics-daily-report",
                session.getPlugin(), templateData(row, store == null ? "" : store.getUpdatedAt())));
        request.setLevel("info");
        request.setRequestId(UUID.randomUUID().toString());
        request.setPayload(payload(row, store == null ? "" : store.getUpdatedAt()));
        return request;
    }

    private static void publish(IOSession session, NotificationRequest request) {
        int msgId = session.publishNotification(request, null);
        MsgPacket response = session.getResponseMsgPacketByMsgId(msgId, NOTIFICATION_TIMEOUT);
        if (response == null) {
            throw new IllegalStateException("notification publish response timeout");
        }
        if (response.getStatus() != MsgPacketStatus.RESPONSE_SUCCESS) {
            throw new IllegalStateException("notification publish failed " + response.getStatus());
        }
    }

    private static StatisticsDailySiteData reportRow(StatisticsDailySiteDataStore store) {
        String targetDate = DAY_FORMATTER.format(LocalDate.now(ZoneId.systemDefault()).minusDays(1));
        if (store != null) {
            List<StatisticsDailySiteData> items = store.getItems();
            for (StatisticsDailySiteData item : items) {
                if (item != null && targetDate.equals(item.getDate())) {
                    return item;
                }
            }
            if (!items.isEmpty() && items.get(0) != null) {
                return items.get(0);
            }
        }
        StatisticsDailySiteData empty = new StatisticsDailySiteData();
        empty.setDate(targetDate);
        empty.setTopArticle("");
        empty.setTopSource("");
        return empty;
    }

    private static String textWithCount(String text, int count) {
        if (text == null || text.trim().isEmpty()) {
            return "-";
        }
        return count > 0 ? text + " (" + count + ")" : text;
    }

    private static Map<String, Object> templateData(StatisticsDailySiteData row, String updatedAt) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", escape(row.getDate()));
        map.put("pv", row.getPv());
        map.put("uv", row.getUv());
        map.put("sessions", row.getSessions());
        map.put("uniqueIp", row.getUniqueIp());
        map.put("articleCount", row.getArticleCount());
        map.put("topArticle", escape(textWithCount(row.getTopArticle(), row.getTopArticleViews())));
        map.put("topArticleViews", row.getTopArticleViews());
        map.put("topSource", escape(textWithCount(row.getTopSource(), row.getTopSourceViews())));
        map.put("topSourceViews", row.getTopSourceViews());
        map.put("mobile", row.getMobile());
        map.put("tablet", row.getTablet());
        map.put("desktop", row.getDesktop());
        map.put("unknownDevice", row.getUnknownDevice());
        map.put("deviceSummary", "移动 " + row.getMobile() + " / 平板 " + row.getTablet()
                + " / 桌面 " + row.getDesktop() + " / 未知 " + row.getUnknownDevice());
        map.put("updatedAt", escape(updatedAt));
        return map;
    }

    private static Map<String, Object> payload(StatisticsDailySiteData row, String updatedAt) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", row.getDate());
        map.put("pv", row.getPv());
        map.put("uv", row.getUv());
        map.put("sessions", row.getSessions());
        map.put("uniqueIp", row.getUniqueIp());
        map.put("articleCount", row.getArticleCount());
        map.put("topArticle", row.getTopArticle());
        map.put("topArticleViews", row.getTopArticleViews());
        map.put("topSource", row.getTopSource());
        map.put("topSourceViews", row.getTopSourceViews());
        map.put("mobile", row.getMobile());
        map.put("tablet", row.getTablet());
        map.put("desktop", row.getDesktop());
        map.put("unknownDevice", row.getUnknownDevice());
        map.put("updatedAt", updatedAt);
        return map;
    }

    private static Map<String, Object> failureTemplateData(String errorMessage) {
        Map<String, Object> map = new HashMap<>();
        map.put("errorMessage", escape(errorMessage == null ? "" : errorMessage));
        return map;
    }

    private static Map<String, Object> failurePayload(String errorMessage) {
        Map<String, Object> map = new HashMap<>();
        map.put("errorMessage", errorMessage == null ? "" : errorMessage);
        return map;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
