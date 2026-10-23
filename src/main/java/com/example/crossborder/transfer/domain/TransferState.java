package com.example.crossborder.transfer.domain;

public enum TransferState {
    PENDING,
    COMPLETED,
    FAILED,
    FLAGGED;

    public TransferState transitionTo(TransferState target) {
        if (this != PENDING || (target != COMPLETED && target != FAILED && target != FLAGGED)) {
            throw new IllegalStateException("Illegal transfer state transition: " + this + " -> " + target);
        }
        return target;
    }
}