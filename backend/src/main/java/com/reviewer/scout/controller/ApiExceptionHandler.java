package com.reviewer.scout.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;
import com.reviewer.scout.service.ModelConnectionException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.reviewer.scout.service.EvidenceValidationException.class) public ResponseEntity<?> evidence(com.reviewer.scout.service.EvidenceValidationException e) { return ResponseEntity.badRequest().body(Map.of("error",e.getMessage(),"code",e.getCode(),"path",e.getPath())); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class) public ResponseEntity<?> status(org.springframework.web.server.ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason() == null ? "请求未完成，请重试" : e.getReason())); }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, org.springframework.dao.DataIntegrityViolationException.class}) public ResponseEntity<?> conflict(Exception e) { return ResponseEntity.status(409).body(Map.of("error", "记录已更新，请刷新后重试；本次修改未保存。")); }
    @ExceptionHandler(ModelConnectionException.class) public ResponseEntity<?> modelFailure(ModelConnectionException e) {
        int status = switch (e.getCode()) { case "timeout" -> 504; case "upstream_unavailable", "rate_limited" -> 503; case "network_error", "tls_error", "invalid_response" -> 502; default -> 422; };
        return ResponseEntity.status(status).body(ModelDiagnostics.describe(e));
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "请求参数不正确" : e.getMessage())); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> unavailable(IllegalStateException e) { return ResponseEntity.status(502).body(Map.of("error", e.getMessage() == null ? "服务暂时不可用" : e.getMessage())); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("error", "请求格式不正确，请检查日期和输入内容")); }
}
