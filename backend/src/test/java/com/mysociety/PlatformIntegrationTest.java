package com.mysociety;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  static final String PASSWORD = "Test-" + UUID.randomUUID();
  static String owner,
      secretary,
      guard,
      resident,
      other,
      societyA,
      societyB,
      flatA,
      flatB,
      membershipB;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/mysociety_test"));
    r.add("app.jwt-secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
    r.add("INITIAL_SUPER_ADMIN_NAME", () -> "Test Owner");
    r.add("INITIAL_SUPER_ADMIN_EMAIL", () -> "owner@test.local");
    r.add("INITIAL_SUPER_ADMIN_PASSWORD", () -> PASSWORD);
    r.add("DEMO_PASSWORD", () -> PASSWORD);
    r.add("spring.flyway.clean-disabled", () -> false);
    r.add("app.upload-dir", () -> "./target/test-uploads");
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class CleanTestDatabase {
    @org.springframework.context.annotation.Bean
    org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy testMigrations() {
      return flyway -> {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
          if (!connection.getMetaData().getURL().matches("jdbc:postgresql://[^/]+/mysociety_test"))
            throw new IllegalStateException(
                "Tests may reset only the dedicated mysociety_test database.");
        } catch (java.sql.SQLException e) {
          throw new IllegalStateException(e);
        }
        flyway.clean();
        flyway.migrate();
      };
    }
  }

  JsonNode request(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, int status)
      throws Exception {
    return json.readTree(
        mvc.perform(b)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
      String token,
      String society) {
    b.header("Authorization", "Bearer " + token);
    if (society != null) b.header("X-Society-Id", society);
    return b;
  }

  JsonNode postJson(String path, String token, String s, Object body, int status) throws Exception {
    return request(
        auth(post("/api/v1" + path), token, s)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)),
        status);
  }

  String login(String email) throws Exception {
    return request(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", email, "password", PASSWORD))),
            200)
        .get("token")
        .asText();
  }

  @Test
  @Order(1)
  void authenticationAndContext() throws Exception {
    owner = login("owner@test.local");
    secretary = login("secretary@demo.local");
    guard = login("watchman@demo.local");
    resident = login("resident@demo.local");
    other = login("resident2@demo.local");
    JsonNode memberships = request(auth(get("/api/v1/memberships"), secretary, null), 200);
    for (JsonNode m : memberships) {
      if (m.get("society").get("code").asText().equals("DEMO"))
        societyA = m.get("societyId").asText();
      else {
        societyB = m.get("societyId").asText();
        membershipB = m.get("id").asText();
      }
    }
    assertNotNull(societyA);
    assertNotNull(societyB);
    flatA =
        request(auth(get("/api/v1/context"), resident, societyA), 200).get("flats").get(0).asText();
    flatB = request(auth(get("/api/v1/flats"), other, societyB), 200).get(0).get("id").asText();
    request(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"resident@demo.local\",\"password\":\"incorrect\"}"),
        401);
    mvc.perform(get("/api/v1/flats").header("X-Society-Id", societyA))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(2)
  void crossSocietyAndRoleDenials() throws Exception {
    request(auth(get("/api/v1/flats"), resident, societyB), 403);
    postJson(
        "/visitors",
        guard,
        societyA,
        Map.of("visitorName", "Cross tenant", "mobileNumber", "9000000020", "flatId", flatB),
        403);
    postJson("/platform/societies", resident, null, Map.of("name", "Forbidden", "code", "NO"), 403);
    postJson("/flats", resident, societyA, Map.of("flatNumber", "ZZ-999"), 403);
    request(auth(get("/api/v1/society-users"), resident, societyA), 403);
  }

  @Test
  @Order(3)
  void societySwitchChecksAndDifferentPermissions() throws Exception {
    JsonNode ctx =
        postJson(
            "/memberships/" + membershipB + "/activate-context", secretary, null, Map.of(), 200);
    assertFalse(ctx.get("permissions").toString().contains("MEETING_CREATE"));
    postJson("/memberships/" + membershipB + "/activate-context", resident, null, Map.of(), 403);
    postJson("/notices", secretary, societyB, Map.of("title", "Forbidden", "content", "No"), 403);
  }

  @Test
  @Order(4)
  void fullVisitorLifecycleAndAudit() throws Exception {
    String id =
        postJson(
                "/visitors",
                guard,
                societyA,
                Map.of(
                    "visitorName",
                    "Integration Guest",
                    "mobileNumber",
                    "9000000021",
                    "flatId",
                    flatA),
                200)
            .get("id")
            .asText();
    postJson("/visitors/" + id + "/check-in", guard, societyA, Map.of(), 409);
    postJson("/visitors/" + id + "/approve", secretary, societyA, Map.of(), 403);
    postJson("/visitors/" + id + "/approve", resident, societyA, Map.of(), 200);
    postJson("/visitors/" + id + "/check-in", guard, societyA, Map.of(), 200);
    postJson("/visitors/" + id + "/check-in", guard, societyA, Map.of(), 409);
    JsonNode result = postJson("/visitors/" + id + "/check-out", guard, societyA, Map.of(), 200);
    assertEquals("CHECKED_OUT", result.get("status").asText());
    assertTrue(
        request(auth(get("/api/v1/audit"), secretary, societyA), 200)
            .toString()
            .contains("VISITOR_CHECK_OUT"));
    assertFalse(request(auth(get("/api/v1/notifications"), resident, societyA), 200).isEmpty());
  }

  @Test
  @Order(5)
  void expectedWindowAndDenial() throws Exception {
    String id =
        postJson(
                "/visitors",
                resident,
                societyA,
                Map.of(
                    "visitorName",
                    "Tomorrow",
                    "mobileNumber",
                    "9000000022",
                    "flatId",
                    flatA,
                    "expected",
                    true,
                    "expectedFrom",
                    Instant.now().plusSeconds(86400).toString(),
                    "expectedUntil",
                    Instant.now().plusSeconds(90000).toString()),
                200)
            .get("id")
            .asText();
    postJson("/visitors/" + id + "/check-in", guard, societyA, Map.of(), 409);
    String walkin =
        postJson(
                "/visitors",
                guard,
                societyA,
                Map.of(
                    "visitorName", "Denied Guest", "mobileNumber", "9000000023", "flatId", flatA),
                200)
            .get("id")
            .asText();
    postJson("/visitors/" + walkin + "/deny", resident, societyA, Map.of(), 200);
    postJson("/visitors/" + walkin + "/check-in", guard, societyA, Map.of(), 409);
  }

  @Test
  @Order(6)
  void parcelFlatPrivacy() throws Exception {
    JsonNode parcel =
        postJson(
            "/parcels",
            guard,
            societyA,
            Map.of("flatId", flatA, "courier", "Post", "description", "Test parcel"),
            200);
    String id = parcel.get("id").asText();
    // Secretary is entitled to all parcels, but a resident in another society is not.
    postJson("/parcels/" + id + "/collect", other, societyA, Map.of(), 403);
    postJson("/parcels/" + id + "/collect", resident, societyA, Map.of(), 200);
    postJson("/parcels/" + id + "/collect", resident, societyA, Map.of(), 409);
  }

  @Test
  @Order(7)
  void complaintHistoryAndAccess() throws Exception {
    String id =
        postJson(
                "/complaints",
                resident,
                societyA,
                Map.of(
                    "title", "Integration leak", "description", "Please inspect", "flatId", flatA),
                200)
            .get("id")
            .asText();
    postJson(
        "/complaints/" + id + "/comments",
        resident,
        societyA,
        Map.of("comment", "More details"),
        200);
    request(
        auth(patch("/api/v1/complaints/" + id), secretary, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\",\"comment\":\"Inspection booked\"}"),
        200);
    assertEquals(
        3,
        request(auth(get("/api/v1/complaints/" + id + "/history"), resident, societyA), 200)
            .size());
    request(auth(get("/api/v1/complaints/" + id + "/history"), other, societyA), 403);
  }

  @Test
  @Order(8)
  void noticeAudienceAndAcknowledgement() throws Exception {
    String id =
        postJson(
                "/notices",
                secretary,
                societyA,
                Map.of(
                    "title",
                    "Targeted notice",
                    "content",
                    "Flat-specific update",
                    "audience",
                    "SPECIFIC_FLATS",
                    "targetIds",
                    flatA,
                    "acknowledgementRequired",
                    true),
                200)
            .get("id")
            .asText();
    assertTrue(
        request(auth(get("/api/v1/notices"), resident, societyA), 200).toString().contains(id));
    postJson("/notices/" + id + "/acknowledge", resident, societyA, Map.of(), 200);
    postJson("/notices/" + id + "/acknowledge", resident, societyA, Map.of(), 200);
  }

  @Test
  @Order(9)
  void meetingMinutesLock() throws Exception {
    String uid = request(auth(get("/api/v1/auth/me"), secretary, null), 200).get("id").asText();
    Map<String, Object> body =
        Map.of(
            "title",
            "Integration meeting",
            "venue",
            "Hall",
            "startsAt",
            Instant.now().toString(),
            "meetingType",
            "AGM");
    String id = postJson("/meetings", secretary, societyA, body, 200).get("id").asText();
    String agenda =
        json.writeValueAsString(List.of(Map.of("title", "Test agenda", "decision", "Approved")));
    String attendance =
        json.writeValueAsString(List.of(Map.of("userId", uid, "status", "PRESENT")));
    request(
        auth(put("/api/v1/meetings/" + id + "/mom"), secretary, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of(
                        "agendaJson",
                        agenda,
                        "attendanceJson",
                        attendance,
                        "momStatus",
                        "REVIEW",
                        "resolutions",
                        "Proceed"))),
        200);
    postJson("/meetings/" + id + "/approve-mom", secretary, societyA, Map.of(), 200);
    request(
        auth(put("/api/v1/meetings/" + id), secretary, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)),
        409);
    postJson("/meetings/" + id + "/approve-mom", resident, societyA, Map.of(), 403);
  }

  @Test
  @Order(10)
  void documentStorageAndIsolation() throws Exception {
    MockMultipartFile bad =
        new MockMultipartFile("file", "bad.pdf", "application/pdf", "not a pdf".getBytes());
    request(auth(multipart("/api/v1/documents").file(bad), secretary, societyA), 400);
    MockMultipartFile pdf =
        new MockMultipartFile(
            "file", "../../test.pdf", "application/pdf", "%PDF-1.4\nTest fixture".getBytes());
    JsonNode doc =
        request(
            auth(
                multipart("/api/v1/documents").file(pdf).param("visibility", "COMMITTEE"),
                secretary,
                societyA),
            200);
    assertFalse(doc.has("storageKey"));
    String id = doc.get("id").asText();
    request(auth(get("/api/v1/documents/" + id + "/download"), resident, societyA), 403);
    request(auth(get("/api/v1/documents/" + id + "/download"), other, societyB), 403);
  }

  @Test
  @Order(11)
  void platformCreationBrandingAndSuspension() throws Exception {
    String id =
        postJson(
                "/platform/societies",
                owner,
                null,
                Map.of("name", "Created by API", "code", "API-TEST"),
                200)
            .get("id")
            .asText();
    assertNotNull(id);
    request(
        auth(put("/api/v1/platform/branding"), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"applicationName\":\"SocietyConnect\",\"shortName\":\"Connect\"}"),
        200);
    assertEquals(
        "SocietyConnect",
        request(get("/api/v1/application/config"), 200).get("applicationName").asText());
    request(
        auth(put("/api/v1/platform/societies/" + societyB), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"name\":\"Sai Darshan Demo CHS\",\"code\":\"SAI\",\"status\":\"SUSPENDED\"}"),
        200);
    request(auth(get("/api/v1/flats"), other, societyB), 403);
    request(auth(get("/api/v1/flats"), owner, societyB), 200);
  }

  @Test
  @Order(12)
  void corsAndHealth() throws Exception {
    mvc.perform(
            options("/api/v1/flats")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,X-Society-Id"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    mvc.perform(
            options("/api/v1/flats")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
    assertEquals("UP", request(get("/api/v1/system/health"), 200).get("status").asText());
  }

  @Test
  @Order(13)
  void sameSocietyFlatAndPrivateRecordIsolation() throws Exception {
    String uid =
        postJson(
                "/platform/users",
                owner,
                null,
                Map.of("name", "Neighbour", "email", "neighbour@test.local", "password", PASSWORD),
                200)
            .get("id")
            .asText();
    postJson(
        "/society-memberships",
        owner,
        societyA,
        Map.of("userId", uid, "roles", "RESIDENT_OWNER"),
        200);
    String profile =
        postJson("/residents", owner, societyA, Map.of("name", "Neighbour", "userId", uid), 200)
            .get("id")
            .asText();
    JsonNode flats = request(auth(get("/api/v1/flats"), owner, societyA), 200);
    String differentFlat = null;
    for (JsonNode f : flats)
      if (!f.get("id").asText().equals(flatA)) {
        differentFlat = f.get("id").asText();
        break;
      }
    String mapping =
        postJson(
                "/resident-flat-mappings",
                owner,
                societyA,
                Map.of(
                    "residentId",
                    profile,
                    "flatId",
                    differentFlat,
                    "relationshipType",
                    "OWNER",
                    "startDate",
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()),
                200)
            .get("id")
            .asText();
    String neighbour = login("neighbour@test.local");
    assertTrue(request(auth(get("/api/v1/visitors"), neighbour, societyA), 200).isEmpty());
    assertTrue(request(auth(get("/api/v1/parcels"), neighbour, societyA), 200).isEmpty());
    assertTrue(request(auth(get("/api/v1/complaints"), neighbour, societyA), 200).isEmpty());
    assertTrue(request(auth(get("/api/v1/documents"), neighbour, societyA), 200).isEmpty());
    assertFalse(
        request(auth(get("/api/v1/notices"), neighbour, societyA), 200)
            .toString()
            .contains("Targeted notice"));
    String complaint =
        request(auth(get("/api/v1/complaints"), secretary, societyA), 200)
            .get(0)
            .get("id")
            .asText();
    request(auth(get("/api/v1/complaints/" + complaint + "/history"), neighbour, societyA), 403);
    assertFalse(
        request(auth(get("/api/v1/context"), neighbour, societyA), 200).get("flats").isEmpty());
    request(
        auth(patch("/api/v1/resident-flat-mappings/" + mapping + "/end"), owner, societyA), 200);
    assertTrue(
        request(auth(get("/api/v1/context"), neighbour, societyA), 200).get("flats").isEmpty());
  }

  @Test
  @Order(14)
  void passwordChangeRevokesExistingToken() throws Exception {
    postJson(
        "/auth/change-password",
        resident,
        null,
        Map.of("currentPassword", PASSWORD, "newPassword", "Updated-" + UUID.randomUUID()),
        200);
    request(auth(get("/api/v1/auth/me"), resident, null), 401);
  }
}
