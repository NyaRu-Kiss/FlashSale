package com.flashsale.activity;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityRecoveryVerifierR09Test {
    @Mock ActivityInventoryEventRepository events;
    @Mock ActivityInventoryReconciliationRepository reconciliation;

    @Test void rejectsUnsentOutboxBeforeAnyProjectionRebuild() {
        when(events.unsentBefore(11, 4)).thenReturn(List.of(3L));
        ActivityRecoveryVerifier verifier = new ActivityRecoveryVerifier(events, reconciliation);

        assertThrows(IllegalStateException.class, () -> verifier.verify(activity(), 4));
        verifyNoInteractions(reconciliation);
    }

    @Test void requiresCheckpointAndLedgerReservationMovementConsistency() {
        when(events.unsentBefore(11, 4)).thenReturn(List.of());
        when(events.checkpoint(11)).thenReturn(4L);
        ActivityRecoveryVerifier verifier = new ActivityRecoveryVerifier(events, reconciliation);

        verifier.verify(activity(), 4);
        verify(reconciliation).assertConsistent(11, 4, 20, 20);
    }

    private static Activity activity() {
        OffsetDateTime start = OffsetDateTime.now().minusMinutes(10);
        return new Activity(11, "sale", 3, 100, 20, 20, 2, start, start.plusHours(1), ActivityStatus.PAUSED, false, 7);
    }
}
