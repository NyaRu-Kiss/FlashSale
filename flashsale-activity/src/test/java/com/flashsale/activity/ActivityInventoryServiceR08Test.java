package com.flashsale.activity;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityInventoryServiceR08Test {
    @Mock ActivityRepository activities;
    @Mock ActivityInventoryPort inventory;
    @Mock ActivityInventoryEventRepository events;

    @Test void releaseRemainsAcceptedWhileActivityIsPaused() {
        Activity paused = new Activity(11, "sale", 3, 100, 20, 20, 2,
                OffsetDateTime.now().minusMinutes(10), OffsetDateTime.now().plusHours(1),
                ActivityStatus.PAUSED, false, 7);
        when(activities.find(11)).thenReturn(paused);
        when(inventory.release(paused, 8, 2, "reservation-1")).thenReturn(true);
        when(events.append(11, ActivityInventoryLedger.Kind.RELEASE, 2, 41L, "order", "trace"))
                .thenReturn(new ActivityInventoryLedger.Event(2, ActivityInventoryLedger.Kind.RELEASE, 2));
        com.flashsale.common.trace.TraceContext.set("trace");
        ActivityInventoryService service = new ActivityInventoryService(activities, inventory, events);

        assertEquals(ActivityInventoryLedger.Kind.RELEASE,
                service.release(11, 8, 2, "reservation-1", 41L).kind());
        verify(events).append(11, ActivityInventoryLedger.Kind.RELEASE, 2, 41L, "order", "trace");
    }
}
