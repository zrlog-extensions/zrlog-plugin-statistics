package com.zrlog.plugin.statistics.service;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.api.IPluginService;
import com.zrlog.plugin.api.ScheduledCapability;
import com.zrlog.plugin.api.Service;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.message.CapabilityInvokeResult;
import com.zrlog.plugin.statistics.model.StatisticsDailySiteDataStore;

import java.util.HashMap;
import java.util.Map;

@Service("statistics.dailyReport")
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
        try {
            StatisticsDailySiteDataStore store = StatisticsRepository.getInstance().refreshDailySiteData(session);
            result.setSuccess(true);
            data.put("updatedAt", store.getUpdatedAt());
            data.put("count", store.getItems().size());
            data.put("items", store.getItems());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            data.put("message", e.getMessage());
        }
        result.setData(data);
        session.sendJsonMsg(result, msgPacket.getMethodStr(), msgPacket.getMsgId(),
                result.isSuccess() ? MsgPacketStatus.RESPONSE_SUCCESS : MsgPacketStatus.RESPONSE_ERROR);
    }
}
