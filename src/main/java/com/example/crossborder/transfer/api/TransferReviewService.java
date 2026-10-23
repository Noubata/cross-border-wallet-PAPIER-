package com.example.crossborder.transfer.api;

import java.util.List;
import java.util.UUID;

public interface TransferReviewService {
    List<TransferResponse> getFlaggedTransfers();

    TransferResponse approveFlaggedTransfer(UUID transferId, UUID adminId);

    TransferResponse rejectFlaggedTransfer(UUID transferId, UUID adminId, String reason);
}