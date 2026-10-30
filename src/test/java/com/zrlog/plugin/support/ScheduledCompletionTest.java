package com.zrlog.plugin.support;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.client.ClientActionHandler;
import com.zrlog.plugin.data.codec.*;
import com.zrlog.plugin.message.*;
import com.zrlog.plugin.type.ActionType;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ScheduledCompletionTest {
    @Test public void configurationFailureReturnsCompletedFailureThroughTheActualHandler() {
        ClientActionHandler handler = new com.zrlog.plugin.statistics.StatisticsClientActionHandler();
        assertEquals(1, handler.capabilityLifecycleVersion());
        java.util.concurrent.atomic.AtomicBoolean queried = new java.util.concurrent.atomic.AtomicBoolean();
        List<MsgPacket> responses = new ArrayList<>();
        IOSession session = new IOSession(new MemorySocketChannels.MemoryChannel(), new MemorySocketChannels.MemorySelector(),
                new SocketCodec(new SocketEncode() {
                    @Override public void doEncode(IOSession ignored, MsgPacket packet) { responses.add(packet); }
                }, new SocketDecode(Runnable::run)), handler) {
            @Override public <T> T getResponseSync(ContentType type, Object data, ActionType action, Class<T> target) {
                queried.set(true);
                throw new IllegalStateException("Configuration is unavailable");
            }
        };
        try {
            session.getAttr().put("_pluginServices", Collections.singletonList(com.zrlog.plugin.statistics.service.StatisticsDailyReportService.class));
            CapabilityInvokeRequest request = new CapabilityInvokeRequest();
            request.setCapabilityKey("statistics.dailyReport"); request.setRequestId("scheduled-fixture");
            request.setRequireCompletion(true); request.setPayload(Collections.emptyMap());
            session.dispatchIfOpen(new MsgPacket(request, ContentType.JSON, MsgPacketStatus.SEND_REQUEST, 1,
                    ActionType.CAPABILITY_INVOKE.name()), () -> { });
            assertTrue(queried.get());
            assertEquals(1, responses.size());
            CapabilityInvokeResult result = new Gson().fromJson(responses.get(0).getDataStr(), CapabilityInvokeResult.class);
            assertFalse(result.isSuccess());
            assertTrue(result.isExecutionCompleted());
            assertEquals(MsgPacketStatus.RESPONSE_ERROR, responses.get(0).getStatus());
            assertFalse(result.getErrorMessage().isEmpty());
        } finally { session.close(); }
    }
}
