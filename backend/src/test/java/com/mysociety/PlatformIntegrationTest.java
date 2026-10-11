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
@org.springframework.context.annotation.Import(DemoTestFixtures.class)
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {
  @Autowired MockMvc mvc;
  @Test
  @Order(99)
  void missingRoutesReturnFriendlyNotFound() throws Exception {
    request(get("/api/v1/not-a-real-route").header("Authorization", "Bearer " + owner), 404);
  }
  @Autowired ObjectMapper json;
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
  static int clientNumber = 1;
  String clientAddress;

  @BeforeEach
  void isolatedClientWindow() {
    clientAddress = "127.0.0." + clientNumber++;
  }

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
        mvc.perform(
                b.with(
                    request -> {
                      request.setRemoteAddr(clientAddress);
                      return request;
                    }))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
      String token,
      String society) {
    if (token != null) b.header("Authorization", "Bearer " + token);
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
  @Order(20)
  void publicSignupApprovalAndTenantBoundaries() throws Exception {
    Map<String, String> signup =
        Map.of(
            "name",
            "New Resident",
            "email",
            "signup@test.local",
            "mobile",
            "9876543210",
            "password",
            PASSWORD,
            "confirmPassword",
            PASSWORD);
    postJson("/auth/signup", null, null, signup, 200);
    postJson("/auth/signup", null, null, signup, 409);
    var duplicateMobile = new HashMap<>(signup);
    duplicateMobile.put("email", "different@test.local");
    duplicateMobile.put("mobile", "+91 98765 43210");
    postJson("/auth/signup", null, null, duplicateMobile, 409);
    var elevated = new HashMap<>(signup);
    elevated.put("roles", "SUPER_ADMIN");
    postJson("/auth/signup", null, null, elevated, 400);
    var mismatch = new HashMap<>(signup);
    mismatch.put("confirmPassword", "Not-the-same-password");
    postJson("/auth/signup", null, null, mismatch, 400);
    var weak = new HashMap<>(signup);
    weak.put("password", "short");
    weak.put("confirmPassword", "short");
    postJson("/auth/signup", null, null, weak, 400);
    String token = login("signup@test.local");
    assertFalse(
        request(auth(get("/api/v1/auth/me"), token, null), 200).get("superAdmin").asBoolean());
    assertEquals(0, request(auth(get("/api/v1/memberships"), token, null), 200).size());
    request(auth(get("/api/v1/flats"), token, societyA), 403);
    request(auth(get("/api/v1/platform/users"), token, null), 403);
    String requestId =
        postJson(
                "/my-membership-requests",
                token,
                null,
                Map.of("societyCode", "DEMO", "flatHint", "A-101"),
                200)
            .get("id")
            .asText();
    postJson(
        "/my-membership-requests",
        token,
        null,
        Map.of("societyCode", "DEMO", "flatHint", "A-101"),
        400);
    assertEquals(
        "PENDING",
        request(auth(get("/api/v1/my-membership-requests"), token, null), 200)
            .get(0)
            .get("status")
            .asText());
    request(auth(get("/api/v1/membership-requests"), guard, societyA), 403);
    postJson(
        "/membership-requests/" + requestId + "/review",
        token,
        societyA,
        Map.of("approve", true, "flatId", flatA, "relationship", "OWNER"),
        403);
    postJson(
        "/membership-requests/" + requestId + "/review",
        owner,
        societyB,
        Map.of("approve", true, "flatId", flatB, "relationship", "OWNER"),
        403);
    postJson(
        "/membership-requests/" + requestId + "/review",
        secretary,
        societyA,
        Map.of("approve", true, "flatId", flatB, "relationship", "OWNER"),
        403);
    postJson(
        "/membership-requests/" + requestId + "/review",
        secretary,
        societyA,
        Map.of("approve", true, "flatId", flatA, "relationship", "SECRETARY"),
        400);
    postJson(
        "/membership-requests/" + requestId + "/review",
        secretary,
        societyA,
        Map.of("approve", true, "flatId", flatA, "relationship", "OWNER"),
        200);
    postJson(
        "/membership-requests/" + requestId + "/review",
        secretary,
        societyA,
        Map.of("approve", false),
        400);
    var context = request(auth(get("/api/v1/context"), token, societyA), 200);
    assertEquals(flatA, context.get("flats").get(0).asText());
    assertTrue(context.get("roles").toString().contains("RESIDENT_OWNER"));
    request(auth(get("/api/v1/visitors"), token, societyA), 200);
    request(auth(get("/api/v1/visitors"), token, societyB), 403);
    request(auth(get("/api/v1/society-users"), token, societyA), 403);
    assertTrue(
        request(auth(get("/api/v1/society-users"), secretary, societyA), 200)
            .toString()
            .contains("New Resident"));
    assertTrue(
        request(auth(get("/api/v1/society-users"), secretary, societyA), 200)
            .toString()
            .contains("flatMappings"));
  }

  @Test
  @Order(21)
  void adminCreationListingEditingSuspensionAndLinking() throws Exception {
    String id =
        postJson(
                "/platform/users",
                owner,
                null,
                Map.of("name", "Managed User", "email", "managed@test.local", "password", PASSWORD),
                200)
            .get("id")
            .asText();
    String token = login("managed@test.local");
    JsonNode listed = request(auth(get("/api/v1/platform/users"), owner, null), 200);
    assertTrue(listed.toString().contains("No Society Assigned"));
    request(
        auth(put("/api/v1/platform/users/" + id), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Edited User\",\"email\":\"managed@test.local\"}"),
        200);
    request(auth(get("/api/v1/auth/me"), token, null), 401);
    token = login("managed@test.local");
    request(
        auth(patch("/api/v1/platform/users/" + id), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"SUSPENDED\"}"),
        200);
    request(auth(get("/api/v1/auth/me"), token, null), 401);
    request(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("login", "managed@test.local", "password", PASSWORD))),
        401);
    request(
        auth(patch("/api/v1/platform/users/" + id), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"ACTIVE\"}"),
        200);
    request(auth(get("/api/v1/auth/me"), token, null), 401);
    postJson(
        "/society-memberships", owner, societyA, Map.of("userId", id, "roles", "WATCHMAN"), 200);
    var members = request(auth(get("/api/v1/society-users"), secretary, societyA), 200);
    assertTrue(members.toString().contains("Edited User"));
    String gate = login("managed@test.local");
    request(auth(get("/api/v1/visitors"), gate, societyA), 200);
    request(auth(get("/api/v1/complaints"), gate, societyA), 403);
    var created =
        postJson(
            "/society-users",
            secretary,
            societyA,
            Map.of(
                "name",
                "Direct Society User",
                "email",
                "direct@test.local",
                "password",
                PASSWORD,
                "roles",
                "RESIDENT_TENANT"),
            200);
    assertEquals("ACTIVE", created.get("membership").get("status").asText());
    assertTrue(
        request(auth(get("/api/v1/society-users"), secretary, societyA), 200)
            .toString()
            .contains("Direct Society User"));
    postJson(
        "/society-users",
        secretary,
        societyA,
        Map.of(
            "name",
            "Attack",
            "email",
            "attack@test.local",
            "password",
            PASSWORD,
            "roles",
            "SUPER_ADMIN"),
        400);
    assertFalse(
        request(auth(get("/api/v1/platform/users"), owner, null), 200)
            .toString()
            .contains("attack@test.local"));
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

  @Test
  @Order(15)
  void themesAreValidatedAuthorizedAndIsolated() throws Exception {
    request(
        auth(patch("/api/v1/society/theme"), secretary, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"theme\":\"OCEAN\"}"),
        200);
    assertEquals(
        "OCEAN",
        request(auth(get("/api/v1/context"), guard, societyA), 200)
            .get("society")
            .get("theme")
            .asText());
    assertEquals(
        "FOREST",
        request(auth(get("/api/v1/context"), owner, societyB), 200)
            .get("society")
            .get("theme")
            .asText());
    request(
        auth(patch("/api/v1/society/theme"), secretary, societyB)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"theme\":\"ROSE\"}"),
        403);
    request(
        auth(patch("/api/v1/society/theme"), guard, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"theme\":\"ROSE\"}"),
        403);
    request(
        auth(patch("/api/v1/society/theme"), other, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"theme\":\"ROSE\"}"),
        403);
    request(
        auth(patch("/api/v1/society/theme"), owner, societyB)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"theme\":\"INDIGO\"}"),
        200);
    request(
        auth(patch("/api/v1/society/theme"), owner, societyA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"theme\":\"url(evil)\"}"),
        400);
  }

  JsonNode recover(String email, String code, String password, int expected) throws Exception {
    return request(
        post("/api/v1/auth/forgot-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("login", email, "recoveryCode", code, "newPassword", password))),
        expected);
  }

  @Test
  @Order(16)
  void recoveryWorksForOwnerCommitteeWatchmanAndResidentAndRevokesTokens() throws Exception {
    for (String email :
        List.of(
            "owner@test.local",
            "secretary@demo.local",
            "watchman@demo.local",
            "resident2@demo.local")) {
      String token = login(email);
      postJson("/auth/recovery-codes", token, null, Map.of("currentPassword", "wrong"), 400);
      JsonNode generated =
          postJson("/auth/recovery-codes", token, null, Map.of("currentPassword", PASSWORD), 200);
      assertEquals(5, generated.get("codes").size());
      assertEquals(
          5,
          request(auth(get("/api/v1/auth/recovery-codes"), token, null), 200)
              .get("remaining")
              .asInt());
      String code = generated.get("codes").get(0).asText();
      recover(email, "incorrect-code", PASSWORD, 400);
      recover("unknown@test.local", code, PASSWORD, 400);
      String newPassword = "Recovered-" + UUID.randomUUID();
      recover(email, code, newPassword, 200);
      recover(email, code, newPassword, 400);
      request(auth(get("/api/v1/auth/me"), token, null), 401);
      String newToken =
          request(
                  post("/api/v1/auth/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          json.writeValueAsString(Map.of("login", email, "password", newPassword))),
                  200)
              .get("token")
              .asText();
      assertEquals(
          4,
          request(auth(get("/api/v1/auth/recovery-codes"), newToken, null), 200)
              .get("remaining")
              .asInt());
      JsonNode rotated =
          postJson(
              "/auth/recovery-codes", newToken, null, Map.of("currentPassword", newPassword), 200);
      recover(email, generated.get("codes").get(1).asText(), PASSWORD, 400);
      recover(email, rotated.get("codes").get(0).asText(), PASSWORD, 200);
    }
    owner = login("owner@test.local");
    secretary = login("secretary@demo.local");
    guard = login("watchman@demo.local");
    other = login("resident2@demo.local");
    String auditText = request(auth(get("/api/v1/platform/audit"), owner, null), 200).toString();
    assertTrue(auditText.contains("PASSWORD_RECOVERED"));
    assertFalse(auditText.contains("codeHash"));
    assertFalse(auditText.contains("passwordHash"));
  }

  @Test
  @Order(17)
  void ownerCanChangeLoginDetailsWithoutLosingGlobalIdentity() throws Exception {
    String id = request(auth(get("/api/v1/auth/me"), owner, null), 200).get("id").asText();
    request(
        auth(put("/api/v1/auth/login-details"), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", "owner-new@test.local", "currentPassword", "wrong"))),
        400);
    request(
        auth(put("/api/v1/auth/login-details"), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD))),
        400);
    request(
        auth(put("/api/v1/auth/login-details"), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", "secretary@demo.local", "currentPassword", PASSWORD))),
        409);
    request(
        auth(put("/api/v1/auth/login-details"), owner, null)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("email", "owner-new@test.local", "currentPassword", PASSWORD))),
        200);
    request(auth(get("/api/v1/auth/me"), owner, null), 401);
    owner = login("owner-new@test.local");
    JsonNode me = request(auth(get("/api/v1/auth/me"), owner, null), 200);
    assertEquals(id, me.get("id").asText());
    assertTrue(me.get("superAdmin").asBoolean());
  }

  @Test
  @Order(18)
  void ownerAssistedRecoveryRejectsSocietyAdminsAndCannotOverrideOwner() throws Exception {
    String email = "assisted@test.local";
    String userId =
        postJson(
                "/platform/users",
                owner,
                null,
                Map.of("name", "Recovery Test", "email", email, "password", PASSWORD),
                200)
            .get("id")
            .asText();
    String token = login(email);
    String code =
        postJson("/auth/recovery-codes", token, null, Map.of("currentPassword", PASSWORD), 200)
            .get("codes")
            .get(0)
            .asText();
    Map<String, String> body =
        Map.of("currentPassword", PASSWORD, "newPassword", "Temporary-" + UUID.randomUUID());
    postJson("/auth/admin-reset/" + userId, secretary, null, body, 403);
    String ownerId = request(auth(get("/api/v1/auth/me"), owner, null), 200).get("id").asText();
    postJson("/auth/admin-reset/" + ownerId, owner, null, body, 400);
    postJson(
        "/auth/admin-reset/" + userId,
        owner,
        null,
        Map.of("currentPassword", "incorrect", "newPassword", PASSWORD),
        400);
    postJson("/auth/admin-reset/" + userId, owner, null, body, 200);
    request(auth(get("/api/v1/auth/me"), token, null), 401);
    recover(email, code, PASSWORD, 400);
  }

  @Test
  @Order(19)
  void simultaneousRecoveryConsumesACodeOnlyOnce() throws Exception {
    String email = "race@test.local";
    postJson(
        "/platform/users",
        owner,
        null,
        Map.of("name", "Concurrent Recovery", "email", email, "password", PASSWORD),
        200);
    String token = login(email);
    String code =
        postJson("/auth/recovery-codes", token, null, Map.of("currentPassword", PASSWORD), 200)
            .get("codes")
            .get(0)
            .asText();
    String body =
        json.writeValueAsString(
            Map.of(
                "login",
                email,
                "recoveryCode",
                code,
                "newPassword",
                "Concurrent-" + UUID.randomUUID()));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Integer> operation =
          () ->
              mvc.perform(
                      post("/api/v1/auth/forgot-password")
                          .with(
                              r -> {
                                r.setRemoteAddr(clientAddress);
                                return r;
                              })
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(body))
                  .andReturn()
                  .getResponse()
                  .getStatus();
      var results = pool.invokeAll(List.of(operation, operation));
      List<Integer> statuses = new ArrayList<>(List.of(results.get(0).get(), results.get(1).get()));
      Collections.sort(statuses);
      assertEquals(List.of(200, 400), statuses);
    }
  }

  @Test
  @Order(22)
  void expiredAndTamperedTokensAndSignupRateLimit() throws Exception {
    String id = request(auth(get("/api/v1/auth/me"), owner, null), 200).get("id").asText();
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .issuer("my-society-api")
            .subject(id)
            .issuedAt(Instant.now().minusSeconds(7200))
            .expiresAt(Instant.now().minusSeconds(3600))
            .claim("revision", 0)
            .build();
    String expired =
        jwtEncoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                    org.springframework.security.oauth2.jwt.JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build(),
                    claims))
            .getTokenValue();
    request(auth(get("/api/v1/auth/me"), expired, null), 401);
    request(
        auth(get("/api/v1/auth/me"), owner.substring(0, owner.length() - 8) + "AAAAAAAA", null),
        401);
    for (int i = 0; i < 61; i++) {
      int expected = i < 60 ? 400 : 429;
      mvc.perform(
              post("/api/v1/auth/signup")
                  .with(
                      q -> {
                        q.setRemoteAddr("127.8.8.8");
                        return q;
                      })
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().is(expected));
    }
  }
}
