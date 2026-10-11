package com.mysociety.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysociety.common.Errors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestFilter extends OncePerRequestFilter {
  private final Map<String, Window> logins = new ConcurrentHashMap<>();

  private record Window(long start, int count) {}

  private final ObjectMapper json;

  public RequestFilter(ObjectMapper json) {
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String correlation = UUID.randomUUID().toString();
    res.setHeader("X-Correlation-Id", correlation);
    res.setHeader("Cache-Control", "no-store");
    org.slf4j.MDC.put("correlationId", correlation);
    try {
      if (req.getRequestURI().startsWith("/api/v1/auth/") && req.getMethod().equals("POST")) {
        long now = System.currentTimeMillis();
        logins.entrySet().removeIf(e -> now - e.getValue().start > 300000);
        // Ignore untrusted forwarding headers. ngrok requests may share this network limit.
        Window w =
            logins.compute(
                req.getRemoteAddr(),
                (k, v) -> v == null ? new Window(now, 1) : new Window(v.start, v.count + 1));
        if (w.count > 60 || logins.size() > 10000) {
          res.setStatus(429);
          res.setHeader("Retry-After", "300");
          res.setContentType("application/json");
          json.writeValue(
              res.getOutputStream(),
              Errors.body(429, "Too many sign-in attempts. Try again in five minutes.", Map.of()));
          return;
        }
      }
      chain.doFilter(req, res);
    } finally {
      org.slf4j.MDC.clear();
    }
  }
}
