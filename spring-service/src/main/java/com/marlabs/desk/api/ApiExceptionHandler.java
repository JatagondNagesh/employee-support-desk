package com.marlabs.desk.api;

import com.marlabs.desk.api.dto.ErrorBody;
import com.marlabs.desk.error.BadRequestException;
import com.marlabs.desk.error.EngineException;
import com.marlabs.desk.error.UnknownCallerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** The single place exceptions become {@code {"error": {"code", "message"}}} plus a status. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UnknownCallerException.class)
    public ResponseEntity<ErrorBody> unknownCaller(UnknownCallerException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorBody.of("UNKNOWN_CALLER", e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorBody> badRequest(BadRequestException e) {
        return ResponseEntity.badRequest().body(ErrorBody.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorBody> malformedRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(ErrorBody.of("INVALID_REQUEST",
                        "Request body or parts are missing or malformed."));
    }

    /** Dependency failures never degrade to a business outcome. */
    @ExceptionHandler(EngineException.class)
    public ResponseEntity<ErrorBody> engineFailure(EngineException e) {
        log.warn("engine_failure code={}", e.code());
        HttpStatus status = HttpStatus.resolve(e.httpStatus());
        if (status == null || !status.isError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status).body(ErrorBody.of(e.code(), e.getMessage()));
    }
}
