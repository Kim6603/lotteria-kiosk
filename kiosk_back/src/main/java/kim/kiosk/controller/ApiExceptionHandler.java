package kim.kiosk.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e, HttpServletRequest req) {
        log.error("[{} {}] {}", req.getMethod(), req.getRequestURI(), e.toString(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "ok", false,
                        "error", e.getClass().getSimpleName(),
                        "message", e.getMessage() == null ? "" : e.getMessage()
                ));
    }
}
