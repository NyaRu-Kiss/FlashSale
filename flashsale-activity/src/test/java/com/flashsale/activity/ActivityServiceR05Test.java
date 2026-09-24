package com.flashsale.activity;

import com.flashsale.common.security.Principal;
import com.flashsale.common.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ActivityServiceR05Test {
    @Mock ActivityRepository repository;
    @Mock ActivityInventoryPort inventory;
    @Mock ActivityRecoveryRepository recoveries;
    @Mock ActivityInventoryEventRepository events;

    private final Principal operator = new Principal(7, Role.OPERATOR);

    @AfterEach void clearTrace() { com.flashsale.common.trace.TraceContext.clear(); }

    @Test void createDoesNotPreheatRedis() {
        Activity input = activity(0, OffsetDateTime.now().plusHours(1));
        Activity created = activity(11, input.startsAt());
        when(repository.create(input, operator.userId())).thenReturn(created);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertEquals(created, service.create(operator, input));
        verify(inventory, never()).preheat(any());
        verify(repository).create(input, operator.userId());
    }

    @Test void cancelWritesImmutableActivityAudit() {
        Activity existing = activity(11, OffsetDateTime.now().plusMinutes(1));
        Activity cancelled = new Activity(existing.id(), existing.name(), existing.productId(), existing.salePriceMinor(),
                existing.initialStock(), existing.availableStock(), existing.purchaseLimitPerUser(), existing.startsAt(),
                existing.endsAt(), ActivityStatus.CANCELLED, false, operator.userId());
        when(repository.find(11)).thenReturn(existing);
        when(repository.casStatus(11, ActivityStatus.NOT_STARTED, ActivityStatus.CANCELLED, operator.userId())).thenReturn(cancelled);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertEquals(cancelled, service.cancel(operator, 11));
        verify(repository).audit(operator.userId(), 11, "CANCEL", existing, cancelled);
    }

    @Test void preheatTaskInitializesCandidateAndWritesReadyEvent() {
        Activity candidate = activity(11, OffsetDateTime.now().plusMinutes(5));
        when(repository.findPreheatCandidate(11, java.time.Duration.ofMinutes(10))).thenReturn(candidate);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertEquals(candidate, service.preheat(11));
        verify(inventory).preheat(candidate);
        verify(repository).recordPreheatReady(eq(candidate), anyString());
    }

    @Test void batchPreheatIsIdempotentAtTaskBoundary() {
        Activity first = activity(11, OffsetDateTime.now().plusMinutes(5));
        Activity second = activity(12, OffsetDateTime.now().plusMinutes(9));
        when(repository.findPreheatCandidates(java.time.Duration.ofMinutes(10))).thenReturn(List.of(first, second));
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertEquals(2, service.preheatDueActivities());
        verify(inventory).preheat(first);
        verify(inventory).preheat(second);
        verify(repository).recordPreheatReady(eq(first), anyString());
        verify(repository).recordPreheatReady(eq(second), anyString());
    }

    @Test void startRequiresPreheatedRedisKeysAndDoesNotRebuildStock() {
        Activity existing = activity(11, OffsetDateTime.now().minusMinutes(1));
        when(repository.find(11)).thenReturn(existing);
        when(inventory.hasPreheatedKeys(11)).thenReturn(false);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertThrows(IllegalArgumentException.class, () -> service.start(operator, 11));
        verify(repository, never()).casStatusAt(anyLong(), any(), any(), anyLong());
        verify(inventory, never()).activate(any());
        verify(inventory, never()).rebuild(any(), anyInt());
    }

    @Test void startActivatesPreheatedKeysWithoutOverwritingRealtimeStock() {
        Activity existing = activity(11, OffsetDateTime.now().minusMinutes(1));
        Activity started = new Activity(existing.id(), existing.name(), existing.productId(), existing.salePriceMinor(),
                existing.initialStock(), 7, existing.purchaseLimitPerUser(), existing.startsAt(), existing.endsAt(),
                ActivityStatus.ACTIVE, true, operator.userId());
        when(repository.find(11)).thenReturn(existing);
        when(inventory.hasPreheatedKeys(11)).thenReturn(true);
        when(inventory.activate(started)).thenReturn(true);
        when(repository.casStatusAt(11, ActivityStatus.NOT_STARTED, ActivityStatus.ACTIVE, operator.userId())).thenReturn(started);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertEquals(started, service.start(operator, 11));
        verify(inventory).activate(started);
        verify(inventory, never()).rebuild(any(), anyInt());
    }

    @Test void concurrentStartCasLoserDoesNotActivateRedis() {
        Activity existing = activity(11, OffsetDateTime.now().minusMinutes(1));
        when(repository.find(11)).thenReturn(existing);
        when(inventory.hasPreheatedKeys(11)).thenReturn(true);
        when(repository.casStatusAt(11, ActivityStatus.NOT_STARTED, ActivityStatus.ACTIVE, operator.userId())).thenReturn(null);
        ActivityService service = new ActivityService(repository, inventory, recoveries, events);

        assertThrows(IllegalArgumentException.class, () -> service.start(operator, 11));
        verify(inventory, never()).activate(any());
    }

    private static Activity activity(long id, OffsetDateTime startsAt) {
        return new Activity(id, "sale-" + id, 3, 100, 20, 20, 2, startsAt,
                startsAt.plusHours(1), ActivityStatus.NOT_STARTED, false, 7);
    }
}
