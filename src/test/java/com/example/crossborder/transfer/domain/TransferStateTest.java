package com.example.crossborder.transfer.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferStateTest {

    @Test
    void pendingCanTransitionToTerminalStates() {
        assertEquals(TransferState.COMPLETED, TransferState.PENDING.transitionTo(TransferState.COMPLETED));
        assertEquals(TransferState.FAILED, TransferState.PENDING.transitionTo(TransferState.FAILED));
        assertEquals(TransferState.FLAGGED, TransferState.PENDING.transitionTo(TransferState.FLAGGED));
    }

    @Test
    void terminalStatesCannotTransition() {
        assertThrows(IllegalStateException.class,
                () -> TransferState.COMPLETED.transitionTo(TransferState.FAILED));
        assertThrows(IllegalStateException.class,
                () -> TransferState.PENDING.transitionTo(TransferState.PENDING));
    }
}