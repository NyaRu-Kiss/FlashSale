package com.flashsale.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CouponCacheInvalidationR19Test {
    @Test void outboxCarriesCacheContractAndRealDelay() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = new ObjectMapper();
        new CouponWriteRepository(jdbc, json).outbox(23);

        Object[] args = mockingDetails(jdbc).getInvocations().iterator().next().getArguments();
        assertTrue(((String) args[0]).contains("available_at"));
        assertEquals("COUPON_CACHE_INVALIDATE", args[2]);
        var payload = json.readTree((String) args[6]);
        assertEquals("COUPON_TEMPLATE", payload.get("resource_type").asText());
        assertEquals(23, payload.get("resource_id").asLong());
        assertEquals("cache:coupon-template:claimable:list", payload.get("cache_keys").get(0).asText());
        assertEquals(args[7], payload.get("trace_id").asText());
        assertTrue(((OffsetDateTime) args[8]).isAfter(OffsetDateTime.now()));
    }
}
