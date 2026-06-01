package com.zrlog.plugin.statistics.service;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.api.Capability;
import com.zrlog.plugin.api.IPluginService;
import com.zrlog.plugin.api.ScheduledCapability;
import com.zrlog.plugin.api.Service;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteDataStore;
import com.zrlog.plugin.statistics.model.StatisticsNotificationChannels;
import com.zrlog.plugin.statistics.util.StatisticsNotificationUtils;

import java.util.HashMap;
import java.util.Map;

@Service("statistics.dailyReport")
@Capability(key = "statistics.dailyReport", riskLevel = "medium")
@ScheduledCapability(
        key = "statistics.dailyReport",
        label = "生成每日站点数据",
        description = "按天聚合访问 PV、UV、Session、访客 IP、热门文章、来源和设备分布。",
        defaultCron = "5 0 * * *",
        timeoutSeconds = 60
)
public class StatisticsDailyReportService implements IPluginService {

    @Override
    public void handle(IOSession session, MsgPacket msgPacket) {
        CapabilityInvokeResult result = new CapabilityInvokeResult();
        Map<String, Object> data = new HashMap<>();
        StatisticsNotificationChannels channels = StatisticsNotificationSettingRepository.getInstance().get(session);
        try {
            StatisticsDailySiteDataStore store = StatisticsRepository.getInstance().refreshDailySiteData(session);
            data.put("updatedAt", store.getUpdatedAt());
            data.put("count", store.getItems().size());
            data.put("items", store.getItems());
            try {
                StatisticsNotificationUtils.publishDailyReport(session, store, channels);
                result.setSuccess(true);
                data.put("notificationSuccess", true);
                data.put("notificationChannels", channels.dailyChannels());
            } catch (Exception e) {
                result.setSuccess(false);
                result.setErrorMessage("统计日报通知发布失败：" + e.getMessage());
                data.put("notificationSuccess", false);
                data.put("notificationError", e.getMessage());
                data.put("notificationChannels", channels.dailyChannels());
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            data.put("message", e.getMessage());
            try {
                StatisticsNotificationUtils.publishDailyReportFailure(session, e.getMessage(), channels);
                data.put("failureNotificationSuccess", true);
                data.put("failureNotificationChannels", channels.failedChannels());
            } catch (Exception notificationError) {
                data.put("failureNotificationSuccess", false);
                data.put("failureNotificationError", notificationError.getMessage());
                data.put("failureNotificationChannels", channels.failedChannels());
            }
        }
        result.setData(data);
        session.sendJsonMsg(result, msgPacket.getMethodStr(), msgPacket.getMsgId(),
                result.isSuccess() ? MsgPacketStatus.RESPONSE_SUCCESS : MsgPacketStatus.RESPONSE_ERROR);
    }
}
