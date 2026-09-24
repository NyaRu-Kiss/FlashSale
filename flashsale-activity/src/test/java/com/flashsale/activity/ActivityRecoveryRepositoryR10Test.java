package com.flashsale.activity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActivityRecoveryRepositoryR10Test {
    @Test void successWritesResumedOutboxWithStableIdempotencyKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        ActivityRecoveryRepository repository = new ActivityRecoveryRepository(jdbc);

        repository.succeeded(5, 11, "trace-1");

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().get(1).contains("ACTIVITY_RESUMED"));
        assertTrue(sql.getAllValues().get(1).contains("on conflict (idempotency_key) do nothing"));
    }
}
