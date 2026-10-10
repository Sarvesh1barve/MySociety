package com.mysociety.common;

import com.mysociety.complaint.*;
import com.mysociety.document.*;
import com.mysociety.flat.*;
import com.mysociety.meeting.*;
import com.mysociety.notice.*;
import com.mysociety.parcel.*;
import com.mysociety.resident.*;
import com.mysociety.security.*;
import com.mysociety.task.*;
import com.mysociety.visitor.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class Visibility {
  private final Access access;
  private final Store db;

  public Visibility(Access access, Store db) {
    this.access = access;
    this.db = db;
  }

  public boolean visitor(Visitor v) {
    return access.can(v.societyId, "VISITOR_READ_ALL")
        || access.flats(v.societyId).contains(v.flatId);
  }

  public boolean parcel(Parcel v) {
    return access.can(v.societyId, "PARCEL_READ_ALL")
        || access.flats(v.societyId).contains(v.flatId);
  }

  public boolean complaint(Complaint c) {
    return access.can(c.societyId, "COMPLAINT_READ_ALL") || access.user().id.equals(c.createdBy);
  }

  public boolean meeting(Meeting m) {
    if (access.can(m.societyId, "MEETING_READ_ALL")) return true;
    if (Set.of("DRAFT", "CANCELLED").contains(m.status)) return false;
    return Set.of("AGM", "SGM").contains(m.meetingType)
        || Input.parseIds(m.participantIds).contains(access.user().id);
  }

  public boolean task(ActionTask t) {
    return access.can(t.societyId, "TASK_READ_ALL")
        || access.user().id.equals(t.assignedTo)
        || access.user().id.equals(t.createdBy);
  }

  public boolean document(Document d) {
    return "SOCIETY".equals(d.visibility)
        || access.user().id.equals(d.uploadedBy)
        || ("COMMITTEE".equals(d.visibility) && access.can(d.societyId, "DOCUMENT_COMMITTEE"));
  }

  public boolean notice(Notice n) {
    if (access.can(n.societyId, "NOTICE_CREATE")) return true;
    Instant now = Instant.now();
    if (n.publishDate != null && n.publishDate.isAfter(now)
        || n.expiryDate != null && !n.expiryDate.isAfter(now)) return false;
    return noticeAudience(n);
  }

  public boolean noticeAudience(Notice n) {
    if ("ENTIRE_SOCIETY".equals(n.audience)) return true;
    Set<UUID> flats = access.flats(n.societyId),
        targets = new HashSet<>(Input.parseIds(n.targetIds));
    if ("SPECIFIC_FLATS".equals(n.audience)) return !Collections.disjoint(flats, targets);
    if ("SPECIFIC_WING".equals(n.audience))
      return db.list(Flat.class, n.societyId).stream()
          .anyMatch(f -> flats.contains(f.id) && targets.contains(f.wingId));
    if ("SPECIFIC_FLOOR".equals(n.audience))
      return db.list(Flat.class, n.societyId).stream()
          .anyMatch(f -> flats.contains(f.id) && targets.contains(f.floorId));
    String relation = "OWNERS_ONLY".equals(n.audience) ? "OWNER" : "TENANT";
    UUID uid = access.user().id;
    LocalDate now =
        LocalDate.now(ZoneId.of(db.get(com.mysociety.society.Society.class, n.societyId).timezone));
    return db.list(ResidentFlatMapping.class, n.societyId).stream()
        .anyMatch(
            m ->
                flats.contains(m.flatId)
                    && relation.equals(m.relationshipType)
                    && (m.startDate == null || !m.startDate.isAfter(now))
                    && (m.endDate == null || m.endDate.isAfter(now))
                    && uid.equals(
                        db.tenant(ResidentProfile.class, n.societyId, m.residentId).userId));
  }

  public void require(boolean visible) {
    if (!visible) throw ApiException.forbidden();
  }

  public void attachments(UUID society, String ids) {
    for (UUID id : Input.parseIds(ids)) {
      Document d = db.tenant(Document.class, society, id);
      require(document(d));
    }
  }
}
