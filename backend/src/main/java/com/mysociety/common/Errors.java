package com.mysociety.common;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
  public record ErrorBody(
      Instant timestamp,
      int status,
      String code,
      String message,
      Map<String, String> fieldErrors) {}

  public static ErrorBody body(int code, String message, Map<String, String> fields) {
    return new ErrorBody(
        Instant.now(),
        code,
        code == 400 ? "VALIDATION_ERROR" : HttpStatus.valueOf(code).name(),
        message,
        fields);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    return ResponseEntity.status(e.status).body(body(e.status.value(), e.getMessage(), Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(body(400, "Please check the highlighted fields.", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(body(400, "Invalid request. Check the values and date formats.", Map.of()));
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(
            body(
                409,
                "This record already exists or was changed. Refresh and try again.",
                Map.of()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> size() {
    return ResponseEntity.status(413).body(body(413, "Files must be 10 MB or smaller.", Map.of()));
  }

  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  ResponseEntity<?> missing() {
    return ResponseEntity.status(404)
        .body(body(404, "This page or resource could not be found.", Map.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error(
            "Request failed: type={}, correlationId={}",
            e.getClass().getSimpleName(),
            org.slf4j.MDC.get("correlationId"));
    return ResponseEntity.status(500)
        .body(body(500, "An unexpected error occurred. Please try again.", Map.of()));
  }
}
