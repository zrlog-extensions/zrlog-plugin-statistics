package com.zrlog.plugin.statistics.service;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StatisticsRepositoryTest {

    @Test
    public void shouldPreferCanonicalRealIpHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Forwarded-For", "10.0.0.1");
        headers.put("X-Real-IP", "203.0.113.10");

        assertEquals("203.0.113.10", StatisticsRepository.getInstance().clientIp(headers));
    }

    @Test
    public void shouldUseFirstForwardedForWhenCanonicalHeaderMissing() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertEquals("203.0.113.10", StatisticsRepository.getInstance().clientIp(headers));
    }
}
