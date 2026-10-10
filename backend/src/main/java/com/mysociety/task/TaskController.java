package com.mysociety.task;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.meeting.*;
import com.mysociety.notification.*;
import com.mysociety.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@Transactional
public class TaskController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final NotificationService notifications;

  public TaskController(
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
    access.require(s, "TASK_READ");
    return db.list(ActionTask.class, s).stream().filter(visibility::task).map(Views::of).toList();
  }

  @PostMapping
  public Map<String, Object> create(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "TASK_CREATE");
    Input i = new Input(b);
    ActionTask t = new ActionTask();
    t.societyId = s;
    t.title = i.required("title", 180);
    t.description = i.text("description", 4000);
    t.assignedTo = i.id("assignedTo");
    access.activeUserInSociety(s, t.assignedTo);
    t.meetingId = i.id("meetingId");
    if (t.meetingId != null) {
      Meeting m = db.tenant(Meeting.class, s, t.meetingId);
      visibility.require(visibility.meeting(m));
      if ("APPROVED".equals(m.momStatus))
        throw ApiException.conflict("Cannot add actions to approved minutes.");
    }
    t.agendaItemId = i.text("agendaItemId", 36);
    if (t.agendaItemId != null) {
      if (t.meetingId == null) throw ApiException.bad("An agenda action requires a meeting.");
      try {
        var items =
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(db.tenant(Meeting.class, s, t.meetingId).agendaJson);
        boolean found = false;
        for (var item : items) if (t.agendaItemId.equals(item.path("id").asText())) found = true;
        if (!found) throw ApiException.bad("Agenda item not found in this meeting.");
      } catch (ApiException e) {
        throw e;
      } catch (Exception e) {
        throw ApiException.bad("Invalid agenda item.");
      }
    }
    t.dueDate = i.date("dueDate");
    t.priority = i.choice("priority", "MEDIUM", "LOW", "MEDIUM", "HIGH", "CRITICAL");
    t.status = "OPEN";
    t.createdBy = access.user().id;
    db.save(t);
    notifications.user(
        s,
        t.assignedTo,
        "TASK_ASSIGNED",
        "A new action is assigned to you",
        "Open Actions for details.",
        t);
    audit.log(s, "TASK_CREATED", t, null);
    return Views.of(t);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> update(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "TASK_UPDATE");
    ActionTask t = db.tenant(ActionTask.class, s, id);
    visibility.require(visibility.task(t));
    Input i = new Input(b);
    String before = audit.snapshot(t);
    String status =
        i.choice("status", t.status, "OPEN", "IN_PROGRESS", "BLOCKED", "COMPLETED", "CANCELLED");
    if (status.equals(t.status)) throw ApiException.conflict("Choose a new action status.");
    t.status = status;
    t.completedAt = "COMPLETED".equals(status) ? Instant.now() : null;
    audit.log(s, "TASK_UPDATED", t, before);
    return Views.of(t);
  }
}
