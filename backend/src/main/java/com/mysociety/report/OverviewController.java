package com.mysociety.report;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.complaint.*;
import com.mysociety.emergency.*;
import com.mysociety.flat.*;
import com.mysociety.meeting.*;
import com.mysociety.notice.*;
import com.mysociety.notification.*;
import com.mysociety.parcel.*;
import com.mysociety.resident.*;
import com.mysociety.security.*;
import com.mysociety.society.Society;
import com.mysociety.task.*;
import com.mysociety.visitor.*;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class OverviewController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;

  public OverviewController(Store db, Access access, Visibility visibility, AuditService audit) {
    this.db = db;
    this.access = access;
    this.visibility = visibility;
    this.audit = audit;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(@RequestHeader("X-Society-Id") UUID s) {
    var p = access.permissions(s);
    Map<String, Object> out = new LinkedHashMap<>();
    Map<String, Long> stats = new LinkedHashMap<>();
    if (p.contains("FLAT_READ")) {
      List<Flat> flats = db.list(Flat.class, s);
      stats.put("Total flats", (long) flats.size());
      stats.put(
          "Occupied",
          flats.stream()
              .filter(f -> Set.of("OWNER_OCCUPIED", "TENANT_OCCUPIED").contains(f.occupancyStatus))
              .count());
      stats.put("Vacant", flats.stream().filter(f -> "VACANT".equals(f.occupancyStatus)).count());
    }
    if (p.contains("VISITOR_READ")) {
      var v = db.list(Visitor.class, s).stream().filter(visibility::visitor).toList();
      stats.put("Visitors inside", v.stream().filter(x -> "CHECKED_IN".equals(x.status)).count());
      stats.put(
          "Pending approvals", v.stream().filter(x -> "WAITING_APPROVAL".equals(x.status)).count());
      ZoneId zone = ZoneId.of(db.get(Society.class, s).timezone);
      LocalDate today = LocalDate.now(zone);
      stats.put(
          "Expected today",
          v.stream()
              .filter(
                  x ->
                      "EXPECTED".equals(x.status)
                          && x.expectedFrom.atZone(zone).toLocalDate().equals(today))
              .count());
      out.put("visitors", v.stream().limit(6).map(Views::of).toList());
    }
    if (p.contains("PARCEL_READ"))
      stats.put(
          "Parcels waiting",
          db.list(Parcel.class, s).stream()
              .filter(visibility::parcel)
              .filter(x -> "WAITING".equals(x.status))
              .count());
    if (p.contains("COMPLAINT_READ")) {
      var c =
          db.list(Complaint.class, s).stream()
              .filter(visibility::complaint)
              .filter(x -> !Set.of("RESOLVED", "CLOSED", "REJECTED").contains(x.status))
              .toList();
      stats.put("Open complaints", (long) c.size());
      stats.put(
          "Critical complaints", c.stream().filter(x -> "CRITICAL".equals(x.priority)).count());
      out.put("complaints", c.stream().limit(4).map(Views::of).toList());
    }
    if (p.contains("TASK_READ"))
      stats.put(
          "Pending actions",
          db.list(ActionTask.class, s).stream()
              .filter(visibility::task)
              .filter(x -> !Set.of("COMPLETED", "CANCELLED").contains(x.status))
              .count());
    if (p.contains("NOTICE_READ"))
      out.put(
          "notices",
          db.list(Notice.class, s).stream()
              .filter(visibility::notice)
              .limit(4)
              .map(Views::of)
              .toList());
    if (p.contains("MEETING_READ"))
      out.put(
          "meetings",
          db.list(Meeting.class, s).stream()
              .filter(visibility::meeting)
              .filter(m -> m.startsAt.isAfter(Instant.now()))
              .sorted(Comparator.comparing(m -> m.startsAt))
              .limit(4)
              .map(Views::of)
              .toList());
    out.put("stats", stats);
    return out;
  }

  @GetMapping("/notifications")
  public List<Map<String, Object>> notifications(@RequestHeader("X-Society-Id") UUID s) {
    access.permissions(s);
    UUID uid = access.user().id;
    return db.list(Notification.class, s).stream()
        .filter(n -> uid.equals(n.userId))
        .limit(100)
        .map(Views::of)
        .toList();
  }

  @PostMapping("/notifications/{id}/read")
  public Map<String, Object> read(@RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.permissions(s);
    Notification n = db.tenant(Notification.class, s, id);
    if (!n.userId.equals(access.user().id)) throw ApiException.forbidden();
    if (n.readAt == null) n.readAt = Instant.now();
    return Views.of(n);
  }

  @GetMapping("/emergency-contacts")
  public List<Map<String, Object>> emergency(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "EMERGENCY_READ");
    return db.list(EmergencyContact.class, s).stream()
        .filter(e -> e.active || access.can(s, "EMERGENCY_CONTACT_MANAGE"))
        .map(Views::of)
        .toList();
  }

  @PostMapping("/emergency-contacts")
  public Map<String, Object> createEmergency(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "EMERGENCY_CONTACT_MANAGE");
    EmergencyContact e = new EmergencyContact();
    e.societyId = s;
    apply(e, new Input(b));
    db.save(e);
    audit.log(s, "EMERGENCY_CONTACT_CREATED", e, null);
    return Views.of(e);
  }

  @PutMapping("/emergency-contacts/{id}")
  public Map<String, Object> updateEmergency(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "EMERGENCY_CONTACT_MANAGE");
    EmergencyContact e = db.tenant(EmergencyContact.class, s, id);
    String before = audit.snapshot(e);
    apply(e, new Input(b));
    audit.log(s, "EMERGENCY_CONTACT_UPDATED", e, before);
    return Views.of(e);
  }

  private void apply(EmergencyContact e, Input i) {
    e.name = i.required("name", 120);
    e.category = i.required("category", 80);
    e.phone = i.required("phone", 20);
    if (!e.phone.matches("\\+?[0-9][0-9 -]{1,18}[0-9]"))
      throw ApiException.bad("Enter a valid emergency telephone number.");
    e.description = i.text("description", 500);
    e.active = i.bool("active", true);
  }

  @GetMapping("/audit")
  public List<Map<String, Object>> audit(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "AUDIT_READ");
    return db.query(AuditLog.class, "societyId=:s order by createdAt desc", Map.of("s", s)).stream()
        .limit(500)
        .map(Views::of)
        .toList();
  }

  @GetMapping("/search")
  public List<Map<String, Object>> search(
      @RequestHeader("X-Society-Id") UUID s, @RequestParam String q) {
    var p = access.permissions(s);
    if (q.length() < 2 || q.length() > 100)
      throw ApiException.bad("Search must contain 2 to 100 characters.");
    String term = q.toLowerCase(Locale.ROOT);
    List<Map<String, Object>> rows = new ArrayList<>();
    if (p.contains("FLAT_READ"))
      add(rows, db.list(Flat.class, s), f -> f.flatNumber, term, "flats", x -> true);
    if (p.contains("RESIDENT_READ"))
      add(
          rows,
          db.list(ResidentProfile.class, s),
          r -> r.name,
          term,
          "residents",
          r -> p.contains("RESIDENT_READ_ALL") || access.user().id.equals(r.userId));
    if (p.contains("VISITOR_READ"))
      add(
          rows,
          db.list(Visitor.class, s),
          v -> v.visitorName,
          term,
          "visitors",
          visibility::visitor);
    if (p.contains("PARCEL_READ"))
      add(
          rows,
          db.list(Parcel.class, s),
          v -> v.courier + " " + v.description,
          term,
          "parcels",
          visibility::parcel);
    if (p.contains("COMPLAINT_READ"))
      add(
          rows,
          db.list(Complaint.class, s),
          v -> v.title,
          term,
          "complaints",
          visibility::complaint);
    if (p.contains("MEETING_READ"))
      add(rows, db.list(Meeting.class, s), v -> v.title, term, "meetings", visibility::meeting);
    return rows.stream().limit(50).toList();
  }

  private <T extends BaseEntity> void add(
      List<Map<String, Object>> rows,
      List<T> data,
      java.util.function.Function<T, String> title,
      String q,
      String module,
      Predicate<T> allowed) {
    data.stream()
        .filter(allowed)
        .filter(v -> title.apply(v).toLowerCase(Locale.ROOT).contains(q))
        .forEach(v -> rows.add(Map.of("id", v.id, "title", title.apply(v), "module", module)));
  }

  @GetMapping("/reports/{report}")
  public List<Map<String, Object>> report(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable String report) {
    access.require(s, "REPORT_VIEW");
    return switch (report) {
      case "flats" -> {
        access.require(s, "FLAT_READ");
        yield db.list(Flat.class, s).stream().map(Views::of).toList();
      }
      case "residents" -> {
        access.require(s, "RESIDENT_READ");
        yield db.list(ResidentProfile.class, s).stream()
            .filter(r -> access.can(s, "RESIDENT_READ_ALL") || access.user().id.equals(r.userId))
            .map(Views::of)
            .toList();
      }
      case "visitors-today", "visitors-inside" -> {
        access.require(s, "VISITOR_READ");
        ZoneId zone = ZoneId.of(db.get(Society.class, s).timezone);
        LocalDate today = LocalDate.now(zone);
        yield db.list(Visitor.class, s).stream()
            .filter(visibility::visitor)
            .filter(
                v ->
                    report.equals("visitors-inside")
                        ? "CHECKED_IN".equals(v.status)
                        : v.createdAt.atZone(zone).toLocalDate().equals(today))
            .map(Views::of)
            .toList();
      }
      case "complaints-open" -> {
        access.require(s, "COMPLAINT_READ");
        yield db.list(Complaint.class, s).stream()
            .filter(visibility::complaint)
            .filter(c -> !Set.of("RESOLVED", "CLOSED", "REJECTED").contains(c.status))
            .map(Views::of)
            .toList();
      }
      case "actions-pending" -> {
        access.require(s, "TASK_READ");
        yield db.list(ActionTask.class, s).stream()
            .filter(visibility::task)
            .filter(t -> !Set.of("COMPLETED", "CANCELLED").contains(t.status))
            .map(Views::of)
            .toList();
      }
      case "parcels-waiting" -> {
        access.require(s, "PARCEL_READ");
        yield db.list(Parcel.class, s).stream()
            .filter(visibility::parcel)
            .filter(p -> "WAITING".equals(p.status))
            .map(Views::of)
            .toList();
      }
      default -> throw ApiException.missing();
    };
  }

  @GetMapping(value = "/reports/{report}/csv", produces = "text/csv")
  public ResponseEntity<String> csv(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable String report) {
    List<Map<String, Object>> rows = report(s, report);
    if (rows.isEmpty()) return ResponseEntity.ok("");
    List<String> keys = new ArrayList<>(rows.getFirst().keySet());
    StringBuilder csv = new StringBuilder(String.join(",", keys)).append("\r\n");
    for (var row : rows) {
      csv.append(
              keys.stream()
                  .map(k -> cell(row.get(k)))
                  .collect(java.util.stream.Collectors.joining(",")))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + report + ".csv\"")
        .body(csv.toString());
  }

  private String cell(Object value) {
    String v = value == null ? "" : value.toString();
    if (v.matches("^[\\s]*[=+@-].*")) v = "'" + v;
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }
}
