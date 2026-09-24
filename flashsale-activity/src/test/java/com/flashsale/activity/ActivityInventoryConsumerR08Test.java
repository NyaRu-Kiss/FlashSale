package com.flashsale.activity;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityInventoryConsumerR08Test {
    @Mock ActivityInventoryEventRepository events;
    @Mock JdbcTemplate jdbc;

    @Test void duplicateMessageIsIgnoredByContiguousCheckpoint() {
        when(events.checkpoint(11)).thenReturn(3L);
        ActivityInventoryConsumer consumer = new ActivityInventoryConsumer(events, jdbc);
        ActivityInventoryMessage message = message(11, 3, ActivityInventoryLedger.Kind.RESERVE);

        assertFalse(consumer.consume(message));
        verify(events, never()).event(anyLong(), anyLong());
        verifyNoInteractions(jdbc);
    }

    @Test void outOfOrderMessageDoesNotCrossCheckpointGap() {
        when(events.checkpoint(11)).thenReturn(3L);
        ActivityInventoryConsumer consumer = new ActivityInventoryConsumer(events, jdbc);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.consume(message(11, 5, ActivityInventoryLedger.Kind.RELEASE)));
        verify(events, never()).event(anyLong(), anyLong());
        verifyNoInteractions(jdbc);
    }

    @Test void successfulMessageProjectsThenAdvancesCheckpoint() {
        when(events.checkpoint(11)).thenReturn(3L);
        when(events.event(11, 4)).thenReturn(new ActivityInventoryEventRepository.EventData(4,
                ActivityInventoryLedger.Kind.RELEASE, 2));
        when(jdbc.update(anyString(), eq(2), eq(11L), eq(2))).thenReturn(1);
        ActivityInventoryConsumer consumer = new ActivityInventoryConsumer(events, jdbc);

        assertTrue(consumer.consume(message(11, 4, ActivityInventoryLedger.Kind.RELEASE)));
        verify(events).advanceCheckpoint(11, 4);
    }

    @Test void projectionFailureLeavesCheckpointForMqRetry() {
        when(events.checkpoint(11)).thenReturn(3L);
        when(events.event(11, 4)).thenReturn(new ActivityInventoryEventRepository.EventData(4,
                ActivityInventoryLedger.Kind.RESERVE, -2));
        when(jdbc.update(anyString(), eq(-2), eq(11L), eq(-2))).thenReturn(0);
        ActivityInventoryConsumer consumer = new ActivityInventoryConsumer(events, jdbc);

        assertThrows(IllegalArgumentException.class,
                () -> consumer.consume(message(11, 4, ActivityInventoryLedger.Kind.RESERVE)));
        verify(events, never()).advanceCheckpoint(anyLong(), anyLong());
    }

    private static ActivityInventoryMessage message(long activityId, long sequence, ActivityInventoryLedger.Kind kind) {
        UUID id = UUID.randomUUID();
        return new ActivityInventoryMessage(id, "ACTIVITY_INVENTORY:" + id, activityId, sequence, kind, 1, "trace");
    }
}
