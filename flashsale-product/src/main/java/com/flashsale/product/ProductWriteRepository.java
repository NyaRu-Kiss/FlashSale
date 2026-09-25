package com.flashsale.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.trace.TraceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
final class ProductWriteRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    ProductWriteRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    void audit(long operatorId, long targetId, String action, Product before, Product after) {
        jdbc.update("insert into operator_audit_log(operator_id,target_type,target_id,action,before_snapshot,after_snapshot,trace_id,request_source) values(?, 'PRODUCT', ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)",
                operatorId, targetId, action, snapshot(before), snapshot(after), TraceContext.getOrCreate(), "operator:" + operatorId);
    }

    void outbox(long productId, List<String> cacheKeys) {
        UUID eventId = UUID.randomUUID();
        String traceId = TraceContext.getOrCreate();
        OffsetDateTime availableAt = OffsetDateTime.now().plusSeconds(1);
        String payload = json(Map.of("resource_type", "PRODUCT", "resource_id", productId,
                "cache_keys", cacheKeys, "trace_id", traceId, "delayed_delete", true,
                "planned_at", availableAt.toString()));
        jdbc.update("insert into product_outbox(event_id,event_type,idempotency_key,aggregate_type,aggregate_id,payload,trace_id,available_at) values(?,?,?,?,?,?,?,?)",
                eventId, "PRODUCT_CACHE_INVALIDATE", "PRODUCT_CACHE_INVALIDATE:" + productId + ":" + eventId,
                "PRODUCT", Long.toString(productId), payload, traceId, availableAt);
    }

    private String snapshot(Product value) { return value == null ? null : json(value); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("AUDIT_SERIALIZATION_FAILED", e); }
    }
}
