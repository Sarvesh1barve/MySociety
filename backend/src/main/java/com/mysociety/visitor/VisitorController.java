package com.mysociety.visitor;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.flat.*;
import com.mysociety.notification.*;
import com.mysociety.security.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/visitors")
@Transactional
public class VisitorController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final NotificationService notifications;

  public VisitorController(
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
    access.require(s, "VISITOR_READ");
    return db.list(Visitor.class, s).stream().filter(visibility::visitor).map(Views::of).toList();
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "VISITOR_READ");
    Visitor v = db.tenant(Visitor.class, s, id);
    visibility.require(visibility.visitor(v));
    return Views.of(v);
  }

  @PostMapping
  public Map<String, Object> create(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "VISITOR_CREATE");
    Input i = new Input(b);
    Visitor v = new Visitor();
    v.societyId = s;
    v.flatId = i.requiredId("flatId");
    Flat flat = db.tenant(Flat.class, s, v.flatId);
    if (!flat.active) throw ApiException.bad("Flat is inactive.");
    boolean expected = i.bool("expected", false);
    if (expected || !access.can(s, "VISITOR_READ_ALL")) access.ownFlat(s, v.flatId);
    v.visitorName = i.required("visitorName", 120);
    v.mobileNumber = i.mobile("mobileNumber", true);
    v.visitorType =
        i.choice("visitorType", "GUEST", "GUEST", "DELIVERY", "VENDOR", "CAB", "SERVICE", "OTHER");
    v.purpose = i.text("purpose", 500);
    v.vehicleNumber = i.text("vehicleNumber", 25);
    v.createdBy = access.user().id;
    if (expected) {
      v.expectedFrom = i.instant("expectedFrom");
      v.expectedUntil = i.instant("expectedUntil");
      if (v.expectedFrom == null
          || v.expectedUntil == null
          || !v.expectedUntil.isAfter(v.expectedFrom)
          || !v.expectedUntil.isAfter(Instant.now())
          || Duration.between(v.expectedFrom, v.expectedUntil).toDays() > 7)
        throw ApiException.bad("Provide a valid visitor window of at most seven days.");
      v.status = "EXPECTED";
      v.approvalStatus = "PRE_APPROVED";
      v.approvedBy = access.user().id;
    } else {
      v.status = "WAITING_APPROVAL";
      v.approvalStatus = "PENDING";
    }
    db.save(v);
    audit.log(s, expected ? "EXPECTED_VISITOR_CREATED" : "VISITOR_CREATED", v, null);
    if (!expected)
      notifications.flat(s, v.flatId, "VISITOR_WAITING", "A visitor is waiting for approval", v);
    else
      notifications.permission(
          s, "VISITOR_CHECKIN", "EXPECTED_VISITOR", "An expected visitor has been added", v);
    return Views.of(v);
  }

  @PostMapping("/{id}/{action}")
  public Map<String, Object> action(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id, @PathVariable String action) {
    String permission =
        switch (action) {
          case "approve", "deny" -> "VISITOR_APPROVE";
          case "check-in" -> "VISITOR_CHECKIN";
          case "check-out" -> "VISITOR_CHECKOUT";
          case "cancel" -> "VISITOR_CREATE";
          default -> throw ApiException.missing();
        };
    access.require(s, permission);
    Visitor v = db.tenant(Visitor.class, s, id);
    String before = audit.snapshot(v);
    Instant now = Instant.now();
    switch (action) {
      case "approve", "deny" -> {
        access.ownFlat(s, v.flatId);
        state(v, "WAITING_APPROVAL");
        v.approvalStatus = action.equals("approve") ? "APPROVED" : "DENIED";
        v.status = v.approvalStatus;
        v.approvedBy = access.user().id;
        notifications.permission(
            s,
            "VISITOR_CHECKIN",
            "VISITOR_" + v.status,
            "Visitor " + v.status.toLowerCase(Locale.ROOT),
            v);
      }
      case "check-in" -> {
        if (!Set.of("APPROVED", "EXPECTED").contains(v.status))
          throw ApiException.conflict("Only approved visitors can check in.");
        if ("EXPECTED".equals(v.status)
            && (now.isBefore(v.expectedFrom) || now.isAfter(v.expectedUntil)))
          throw ApiException.conflict(
              "The visitor authorization is outside its valid time window.");
        v.status = "CHECKED_IN";
        v.entryTime = now;
      }
      case "check-out" -> {
        state(v, "CHECKED_IN");
        v.status = "CHECKED_OUT";
        v.exitTime = now;
        if (v.exitTime.isBefore(v.entryTime))
          throw ApiException.conflict("Checkout cannot precede check-in.");
      }
      case "cancel" -> {
        access.ownFlat(s, v.flatId);
        if (!Set.of("EXPECTED", "WAITING_APPROVAL", "APPROVED").contains(v.status))
          throw ApiException.conflict("This visitor cannot be cancelled.");
        v.status = "CANCELLED";
      }
    }
    audit.log(s, "VISITOR_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), v, before);
    return Views.of(v);
  }

  private void state(Visitor v, String expected) {
    if (!expected.equals(v.status))
      throw ApiException.conflict("The visitor status changed. Refresh the list.");
  }
}
