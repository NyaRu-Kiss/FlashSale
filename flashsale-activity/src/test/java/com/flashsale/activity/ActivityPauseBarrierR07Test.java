package com.flashsale.activity;

import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityPauseBarrierR07Test {
    @Mock ActivityRepository repository;
    @Mock ActivityInventoryPort inventory;
    @Mock ActivityRecoveryRepository recoveries;
    @Mock ActivityInventoryEventRepository events;

    private final Principal operator = new Principal(7, Role.OPERATOR);

    @AfterEach void clearTrace() { com.flashsale.common.trace.TraceContext.clear(); }

    @Test void pauseClosesAndDrainsGateBeforeCapturingBarrierAndCas() {
        Activity active = active(11);
        Activity paused = new Activity(active.id(), active.name(), active.productId(), active.salePriceMinor(),
                active.initialStock(), active.availableStock(), active.purchaseLimitPerUser(), active.startsAt(),
                active.endsAt(), ActivityStatus.PAUSED, false, operator.userId());
        when(repository.find(11)).thenReturn(active);
        when(repository.lockAndReadBarrier(11)).thenReturn(17L);
        when(repository.pauseWithBarrier(11, 17L, operator.userId())).thenReturn(paused);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertEquals(paused, service.pause(operator, 11));

        InOrder ordered = inOrder(inventory, repository);
        ordered.verify(inventory).closeGateAndAwaitInFlight(11);
        ordered.verify(repository).lockAndReadBarrier(11);
        ordered.verify(repository).pauseWithBarrier(11, 17L, operator.userId());
    }

    @Test void competingPauseCannotPublishAnotherBarrierAfterCasLoss() {
        when(repository.find(11)).thenReturn(active(11));
        when(repository.lockAndReadBarrier(11)).thenReturn(17L);
        when(repository.pauseWithBarrier(11, 17L, operator.userId())).thenReturn(null);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertThrows(IllegalArgumentException.class, () -> service.pause(operator, 11));
        verify(inventory).closeGateAndAwaitInFlight(11);
        verify(repository).pauseWithBarrier(11, 17L, operator.userId());
    }

    private static Activity active(long id) {
        OffsetDateTime start = OffsetDateTime.now().minusMinutes(1);
        return new Activity(id, "sale-" + id, 3, 100, 20, 20, 2, start,
                start.plusHours(1), ActivityStatus.ACTIVE, false, 7);
    }
}
