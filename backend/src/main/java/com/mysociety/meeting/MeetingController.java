package com.mysociety.meeting;

import com.fasterxml.jackson.databind.*;
import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.notification.*;
import com.mysociety.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meetings")
@Transactional
public class MeetingController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final NotificationService notifications;
  private final ObjectMapper json;

  public MeetingController(
      Store db,
      Access access,
      Visibility visibility,
      AuditService audit,
      NotificationService notifications,
      ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.visibility = visibility;
    this.audit = audit;
    this.notifications = notifications;
    this.json = json;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "MEETING_READ");
    return db.list(Meeting.class, s).stream().filter(visibility::meeting).map(Views::of).toList();
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "MEETING_READ");
    Meeting m = db.tenant(Meeting.class, s, id);
    visibility.require(visibility.meeting(m));
    return Views.of(m);
  }

  @PostMapping
  public Map<String, Object> create(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "MEETING_CREATE");
    Meeting m = new Meeting();
    m.societyId = s;
    apply(m, new Input(b));
    m.momStatus = "DRAFT";
    m.createdBy = access.user().id;
    db.save(m);
    if (!"DRAFT".equals(m.status)) notifyParticipants(m);
    audit.log(s, "MEETING_CREATED", m, null);
    return Views.of(m);
  }

  @PutMapping("/{id}")
  public Map<String, Object> edit(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "MEETING_EDIT");
    Meeting m = db.tenant(Meeting.class, s, id);
    visibility.require(visibility.meeting(m));
    unlocked(m);
    String before = audit.snapshot(m);
    apply(m, new Input(b));
    audit.log(s, "MEETING_UPDATED", m, before);
    return Views.of(m);
  }

  private void apply(Meeting m, Input i) {
    m.title = i.required("title", 180);
    m.meetingType =
        i.choice(
            "meetingType",
            "COMMITTEE",
            "COMMITTEE",
            "AGM",
            "SGM",
            "EMERGENCY",
            "VENDOR",
            "REPAIR_COMMITTEE");
    m.startsAt = i.instant("startsAt");
    if (m.startsAt == null) throw ApiException.bad("Meeting date and time are required.");
    m.venue = i.required("venue", 200);
    m.participantIds = i.ids("participantIds");
    for (UUID id : Input.parseIds(m.participantIds)) access.activeUserInSociety(m.societyId, id);
    m.status =
        i.choice(
            "status",
            "SCHEDULED",
            "DRAFT",
            "SCHEDULED",
            "IN_PROGRESS",
            "COMPLETED",
            "MOM_DRAFT",
            "CANCELLED");
    if (i.has("agendaJson")) m.agendaJson = agenda(i.text("agendaJson", 50000));
    else if (m.agendaJson == null) m.agendaJson = "[]";
    if (m.attendanceJson == null) m.attendanceJson = "[]";
    m.attachmentIds = i.ids("attachmentIds");
    visibility.attachments(m.societyId, m.attachmentIds);
  }

  public record AgendaItem(
      String id,
      int sequenceNumber,
      String title,
      String description,
      String discussion,
      String decision) {}

  public record Attendance(UUID userId, String status) {}

  private String agenda(String source) {
    try {
      List<AgendaItem> list =
          json.readValue(
              source == null ? "[]" : source,
              json.getTypeFactory().constructCollectionType(List.class, AgendaItem.class));
      if (list.size() > 100) throw ApiException.bad("At most 100 agenda items are allowed.");
      List<AgendaItem> out = new ArrayList<>();
      Set<String> ids = new HashSet<>();
      int seq = 1;
      for (AgendaItem a : list) {
        if (a.title() == null || a.title().isBlank() || a.title().length() > 180)
          throw ApiException.bad("Each agenda item needs a title (up to 180 characters).");
        String id =
            a.id() == null || a.id().isBlank()
                ? UUID.randomUUID().toString()
                : UUID.fromString(a.id()).toString();
        if (!ids.add(id)) throw ApiException.bad("Agenda IDs must be unique.");
        out.add(
            new AgendaItem(id, seq++, a.title(), a.description(), a.discussion(), a.decision()));
      }
      return json.writeValueAsString(out);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.bad("Agenda must be a valid array of agenda items.");
    }
  }

  @PutMapping("/{id}/mom")
  public Map<String, Object> mom(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "MOM_CREATE");
    Meeting m = db.tenant(Meeting.class, s, id);
    visibility.require(visibility.meeting(m));
    unlocked(m);
    Input i = new Input(b);
    String before = audit.snapshot(m);
    m.agendaJson = agenda(i.text("agendaJson", 50000));
    m.resolutions = i.text("resolutions", 16000);
    try {
      String raw = i.text("attendanceJson", 20000);
      List<Attendance> attendance =
          json.readValue(
              raw == null ? "[]" : raw,
              json.getTypeFactory().constructCollectionType(List.class, Attendance.class));
      Set<UUID> ids = new HashSet<>();
      for (Attendance a : attendance) {
        if (a.userId() == null
            || !ids.add(a.userId())
            || !Set.of("PRESENT", "ABSENT", "LEAVE_OF_ABSENCE").contains(a.status()))
          throw ApiException.bad("Invalid or duplicate attendance.");
        access.activeUserInSociety(s, a.userId());
      }
      m.attendanceJson = json.writeValueAsString(attendance);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.bad("Attendance must be a valid array.");
    }
    m.momStatus = i.choice("momStatus", "DRAFT", "DRAFT", "REVIEW");
    m.status = "MOM_DRAFT";
    audit.log(s, "MOM_UPDATED", m, before);
    return Views.of(m);
  }

  @PostMapping("/{id}/approve-mom")
  public Map<String, Object> approve(@RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "MOM_APPROVE");
    Meeting m = db.tenant(Meeting.class, s, id);
    visibility.require(visibility.meeting(m));
    unlocked(m);
    if (!"REVIEW".equals(m.momStatus) || "[]".equals(m.agendaJson) || "[]".equals(m.attendanceJson))
      throw ApiException.conflict(
          "Submit minutes with agenda and attendance for review before approval.");
    String before = audit.snapshot(m);
    m.momStatus = "APPROVED";
    m.status = "MOM_APPROVED";
    m.approvedBy = access.user().id;
    m.approvedAt = Instant.now();
    audit.log(s, "MOM_APPROVED", m, before);
    return Views.of(m);
  }

  private void unlocked(Meeting m) {
    if ("APPROVED".equals(m.momStatus))
      throw ApiException.conflict(
          "Approved minutes are locked. Record a correction in a new meeting.");
  }

  private void notifyParticipants(Meeting m) {
    if (Set.of("AGM", "SGM").contains(m.meetingType))
      notifications.society(
          m.societyId, "MEETING_SCHEDULED", "A society meeting has been scheduled", m);
    else
      for (UUID uid : Input.parseIds(m.participantIds))
        notifications.user(
            m.societyId,
            uid,
            "MEETING_SCHEDULED",
            "You have a meeting invitation",
            "Open Meetings for details.",
            m);
  }
}
