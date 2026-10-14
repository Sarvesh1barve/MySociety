package com.mysociety.auth;

import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {
  private final SessionTokens sessions;

  public SessionController(SessionTokens sessions) {
    this.sessions = sessions;
  }

  @PostMapping("/refresh")
  public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
    sessions.browserRequest(request);
    return sessions.refresh(sessions.readCookie(request), response);
  }

  @PostMapping("/logout")
  public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
    sessions.browserRequest(request);
    sessions.logout(sessions.readCookie(request), response);
    return Map.of("message", "Signed out.");
  }
}
