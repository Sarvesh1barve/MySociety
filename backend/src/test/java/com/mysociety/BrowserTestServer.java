package com.mysociety;

/** Explicit, loopback-only browser fixture server. Never packaged in the application JAR. */
public class BrowserTestServer {
  public static void main(String[] args) {
    String url = System.getenv("SPRING_DATASOURCE_URL");
    if (url == null
        || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/mysociety_e2e"))
      throw new IllegalStateException("Browser fixtures may use only loopback mysociety_e2e.");
    new org.springframework.boot.builder.SpringApplicationBuilder(
            MySocietyApplication.class, DemoTestFixtures.class)
        .properties("server.address=127.0.0.1")
        .run(args);
  }
}
