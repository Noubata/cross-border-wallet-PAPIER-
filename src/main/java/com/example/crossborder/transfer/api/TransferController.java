package com.example.crossborder.transfer.api;

import com.example.crossborder.transfer.application.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferResult result = transferService.transfer(UUID.fromString(authentication.getName()),
                idempotencyKey, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @PostMapping("/convert")
    public ResponseEntity<TransferResponse> convert(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConvertTransferRequest request) {
        TransferResult result = transferService.convert(UUID.fromString(authentication.getName()),
                idempotencyKey, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}