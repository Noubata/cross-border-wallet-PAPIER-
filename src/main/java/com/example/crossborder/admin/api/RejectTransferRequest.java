package com.example.crossborder.admin.api;

import jakarta.validation.constraints.Size;

public record RejectTransferRequest(@Size(max = 500) String reason) {
}