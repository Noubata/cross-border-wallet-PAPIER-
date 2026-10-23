package com.example.crossborder.transfer.api;

import org.springframework.http.HttpStatus;

public record TransferResult(HttpStatus status, TransferResponse body) {
}