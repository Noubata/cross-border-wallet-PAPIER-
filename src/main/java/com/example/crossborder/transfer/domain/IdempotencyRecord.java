package com.example.crossborder.transfer.domain;

import java.util.UUID;

public record IdempotencyRecord(UUID id, String requestHash, UUID transferId,
								Integer responseStatus, String responseBody) {

	public IdempotencyRecord(UUID id, String requestHash, UUID transferId, String responseBody) {
		this(id, requestHash, transferId, null, responseBody);
	}
}