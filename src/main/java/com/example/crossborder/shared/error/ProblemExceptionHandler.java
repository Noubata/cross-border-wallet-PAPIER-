package com.example.crossborder.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemExceptionHandler {

    private final ProblemFactory problemFactory;

    public ProblemExceptionHandler(ProblemFactory problemFactory) {
        this.problemFactory = problemFactory;
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
        return problemFactory.create(exception.status(), exception.messageKey(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception,
                                          HttpServletRequest request) {
        return problemFactory.create(HttpStatus.BAD_REQUEST, "request.invalid", request);
    }
}