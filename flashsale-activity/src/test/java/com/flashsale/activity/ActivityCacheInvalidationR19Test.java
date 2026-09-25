package com.flashsale.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;

class ActivityCacheInvalidationR19Test {
    @Test void createEventCarriesResourceKeyAndTrace() throws Exception {
        var created = new Activity(31, "sale", 7, 100, 10, 10, 1,
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(2),
                ActivityStatus.NOT_STARTED, false, 5);
        var jdbc = new CapturingJdbc(created);
        new ActivityRepository(jdbc, new ObjectMapper().findAndRegisterModules()).create(created, 5);

        assertNotNull(jdbc.payload);
        var payload = new ObjectMapper().readTree(jdbc.payload);
        assertEquals("ACTIVITY", payload.get("resource_type").asText());
        assertEquals(31, payload.get("resource_id").asLong());
        assertEquals("cache:activity:public:31", payload.get("cache_keys").get(0).asText());
        assertEquals(jdbc.traceId, payload.get("trace_id").asText());
    }

    private static final class CapturingJdbc extends JdbcTemplate {
        private final Activity created;
        private String payload;
        private String traceId;
        private CapturingJdbc(Activity created) { this.created = created; }
        @Override public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... args) {
            @SuppressWarnings("unchecked") T result = (T) created;
            return result;
        }
        @Override public int update(String sql, Object... args) {
            if (sql.contains("activity_outbox")) {
                payload = (String) args[3];
                traceId = (String) args[4];
            }
            return 1;
        }
    }
}
