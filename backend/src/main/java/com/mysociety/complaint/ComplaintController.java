package com.mysociety.complaint;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.flat.Flat;
import com.mysociety.notification.*;
import com.mysociety.security.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/complaints")
@Transactional
public class ComplaintController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final NotificationService notifications;

  public ComplaintController(
      Store db,
      Access access,
      Visibility visibility,
      AuditService audit,
      NotificationService notifications) {
    this.db = db;
    this.access = access;
    this.visibility = visibility;
    this.audit = audit;
    this.notifications = notifications;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "COMPLAINT_READ");
    return db.list(Complaint.class, s).stream()
        .filter(visibility::complaint)
        .map(Views::of)
        .toList();
  }

  @PostMapping
  public Map<String, Object> create(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "COMPLAINT_CREATE");
    Input i = new Input(b);
    Complaint c = new Complaint();
    c.societyId = s;
    c.title = i.required("title", 160);
    c.description = i.required("description", 6000);
    c.category =
        i.choice(
            "category",
            "OTHER",
            "PLUMBING",
            "ELECTRICAL",
            "LIFT",
            "WATER",
            "SECURITY",
            "CLEANING",
            "PARKING",
            "BUILDING_REPAIR",
            "LEAKAGE",
            "STRUCTURAL_CRACK",
            "CCTV",
            "GARDEN",
            "GARBAGE",
            "NOISE",
            "OTHER");
    c.priority = i.choice("priority", "MEDIUM", "LOW", "MEDIUM", "HIGH", "CRITICAL");
    c.location = i.text("location", 200);
    c.flatId = i.id("flatId");
    if (c.flatId != null) {
      db.tenant(Flat.class, s, c.flatId);
      if (!access.can(s, "COMPLAINT_READ_ALL")) access.ownFlat(s, c.flatId);
    }
    c.attachmentIds = i.ids("attachmentIds");
    visibility.attachments(s, c.attachmentIds);
    c.createdBy = access.user().id;
    c.status = "NEW";
    db.save(c);
    history(c, "Complaint raised");
    notifications.permission(
        s, "COMPLAINT_ASSIGN", "COMPLAINT_NEW", "A new complaint needs attention", c);
    audit.log(s, "COMPLAINT_CREATED", c, null);
    return Views.of(c);
  }

  @GetMapping("/{id}/history")
  public List<Map<String, Object>> history(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "COMPLAINT_READ");
    visibility.require(visibility.complaint(db.tenant(Complaint.class, s, id)));
    return db.list(ComplaintHistory.class, s).stream()
        .filter(h -> h.complaintId.equals(id))
        .map(Views::of)
        .toList();
  }

  @PostMapping("/{id}/comments")
  public Map<String, Object> comment(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "COMPLAINT_READ");
    Complaint c = db.tenant(Complaint.class, s, id);
    visibility.require(visibility.complaint(c));
    ComplaintHistory h = history(c, new Input(b).required("comment", 4000));
    audit.log(s, "COMPLAINT_COMMENTED", h, null);
    notifications.user(
        s,
        c.createdBy,
        "COMPLAINT_UPDATED",
        "A complaint has a new comment",
        "View the complaint timeline.",
        c);
    return Views.of(h);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> update(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "COMPLAINT_UPDATE");
    Complaint c = db.tenant(Complaint.class, s, id);
    visibility.require(visibility.complaint(c));
    String before = audit.snapshot(c);
    Input i = new Input(b);
    String next =
        i.choice(
            "status",
            c.status,
            "NEW",
            "ACKNOWLEDGED",
            "ASSIGNED",
            "IN_PROGRESS",
            "WAITING_FOR_VENDOR",
            "WAITING_FOR_APPROVAL",
            "RESOLVED",
            "CLOSED",
            "REJECTED",
            "REOPENED");
    if (next.equals(c.status) && !i.has("assignedTo"))
      throw ApiException.conflict("Choose a new status or assignee.");
    if (Set.of("CLOSED", "REJECTED").contains(c.status) && !next.equals("REOPENED"))
      throw ApiException.conflict("Reopen this complaint before updating it.");
    if ("REOPENED".equals(next) && !Set.of("RESOLVED", "CLOSED", "REJECTED").contains(c.status))
      throw ApiException.conflict("Only completed complaints can be reopened.");
    if (i.has("assignedTo")) {
      access.require(s, "COMPLAINT_ASSIGN");
      c.assignedTo = i.id("assignedTo");
      access.activeUserInSociety(s, c.assignedTo);
    }
    if ("ASSIGNED".equals(next) && c.assignedTo == null)
      throw ApiException.bad("Select an assignee.");
    c.status = next;
    history(c, i.required("comment", 4000));
    notifications.user(
        s,
        c.createdBy,
        "COMPLAINT_UPDATED",
        "Your complaint was updated",
        "Status: " + c.status,
        c);
    if (c.assignedTo != null)
      notifications.user(
          s,
          c.assignedTo,
          "COMPLAINT_ASSIGNED",
          "A complaint is assigned to you",
          "Open Complaints to view it.",
          c);
    audit.log(s, "COMPLAINT_STATUS_CHANGED", c, before);
    return Views.of(c);
  }

  @PostMapping("/{id}/reopen")
  public Map<String, Object> reopen(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "COMPLAINT_CREATE");
    Complaint c = db.tenant(Complaint.class, s, id);
    if (!c.createdBy.equals(access.user().id)) throw ApiException.forbidden();
    if (!Set.of("RESOLVED", "CLOSED", "REJECTED").contains(c.status))
      throw ApiException.conflict("Only resolved, closed or rejected complaints can be reopened.");
    String before = audit.snapshot(c);
    c.status = "REOPENED";
    history(c, new Input(b).required("comment", 4000));
    audit.log(s, "COMPLAINT_REOPENED", c, before);
    return Views.of(c);
  }

  private ComplaintHistory history(Complaint c, String text) {
    ComplaintHistory h = new ComplaintHistory();
    h.societyId = c.societyId;
    h.complaintId = c.id;
    h.actorId = access.user().id;
    h.status = c.status;
    h.comment = text;
    return db.save(h);
  }
}
