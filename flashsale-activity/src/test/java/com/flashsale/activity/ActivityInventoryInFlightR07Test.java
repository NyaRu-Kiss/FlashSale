package com.flashsale.activity;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityInventoryInFlightR07Test {
    @Mock ActivityRepository activities;
    @Mock ActivityInventoryPort inventory;
    @Mock ActivityInventoryEventRepository events;

    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void rollbackCompensatesRedisBeforeMarkingInFlightRequestComplete() {
        Activity activity = active(11);
        when(activities.find(11)).thenReturn(activity);
        when(inventory.reserve(activity, 8, 2, "request-1"))
                .thenReturn(new ActivityInventoryPort.Reservation(true, 18, "OK"));
        when(inventory.release(activity, 8, 2, "request-1")).thenReturn(true);
        when(events.append(eq(11L), eq(ActivityInventoryLedger.Kind.RESERVE), eq(2), isNull(), eq("order"), anyString()))
                .thenReturn(new ActivityInventoryLedger.Event(1, ActivityInventoryLedger.Kind.RESERVE, 2));
        TransactionSynchronizationManager.initSynchronization();
        ActivityInventoryService service = new ActivityInventoryService(activities, inventory, events);

        service.reserve(11, 8, 2, "request-1", null);

        verify(inventory, never()).completeInFlight(11, "request-1");
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(inventory).release(activity, 8, 2, "request-1");
        verify(inventory).completeInFlight(11, "request-1");
    }

    private static Activity active(long id) {
        OffsetDateTime start = OffsetDateTime.now().minusMinutes(1);
        return new Activity(id, "sale-" + id, 3, 100, 20, 20, 2, start,
                start.plusHours(1), ActivityStatus.ACTIVE, false, 7);
    }
}
