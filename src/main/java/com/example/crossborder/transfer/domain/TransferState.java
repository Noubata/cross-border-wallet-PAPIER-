package com.example.crossborder.transfer.domain;

public enum TransferState {
    PENDING,
    COMPLETED,
    FAILED,
    FLAGGED;

    public TransferState transitionTo(TransferState target) {
        boolean pendingTransition = this == PENDING
                && (target == COMPLETED || target == FAILED || target == FLAGGED);
        boolean reviewTransition = this == FLAGGED && (target == COMPLETED || target == FAILED);
        if (!pendingTransition && !reviewTransition) {
            throw new IllegalStateException("Illegal transfer state transition: " + this + " -> " + target);
        }
        return target;
    }
}