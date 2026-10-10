package com.mysociety.security;

import com.mysociety.common.ApiException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** In-memory account throttle, additional to the request/IP limit. No login values are logged. */
@Component
public class LoginThrottle {
  private record Attempt(long started, int failures) {}

  private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

  public void check(String login) {
    long now = System.currentTimeMillis();
    attempts.entrySet().removeIf(e -> now - e.getValue().started() > 900000);
    Attempt attempt = attempts.get(login);
    if (attempt != null && attempt.failures() >= 12 || attempts.size() > 10000)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Try again in fifteen minutes.");
  }

  public void failed(String login) {
    attempts.compute(
        login,
        (key, value) ->
            value == null
                ? new Attempt(System.currentTimeMillis(), 1)
                : new Attempt(value.started(), value.failures() + 1));
  }

  public void success(String login) {
    attempts.remove(login);
  }
}
