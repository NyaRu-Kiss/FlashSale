package com.flashsale.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCacheInvalidationR19Test {
    @Test void outboxCarriesCacheContractAndRealDelay() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = new ObjectMapper();
        new ProductWriteRepository(jdbc, json).outbox(17, List.of("cache:product:public:17", "cache:product:public:list"));

        var invocation = mockingDetails(jdbc).getInvocations().iterator().next();
        Object[] args = invocation.getArguments();
        assertTrue(((String) args[0]).contains("available_at"));
        assertEquals("PRODUCT_CACHE_INVALIDATE", args[2]);
        var payload = json.readTree((String) args[6]);
        assertEquals("PRODUCT", payload.get("resource_type").asText());
        assertEquals(17, payload.get("resource_id").asLong());
        assertEquals("cache:product:public:17", payload.get("cache_keys").get(0).asText());
        assertEquals(args[7], payload.get("trace_id").asText());
        assertTrue(((OffsetDateTime) args[8]).isAfter(OffsetDateTime.now()));
    }
}
