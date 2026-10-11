package com.mysociety.common;

import com.mysociety.audit.AuditLog;
import com.mysociety.complaint.Complaint;
import com.mysociety.complaint.ComplaintHistory;
import com.mysociety.document.Document;
import com.mysociety.emergency.EmergencyContact;
import com.mysociety.flat.Building;
import com.mysociety.flat.Flat;
import com.mysociety.flat.Floor;
import com.mysociety.flat.Wing;
import com.mysociety.meeting.Meeting;
import com.mysociety.membership.Membership;
import com.mysociety.notice.Acknowledgement;
import com.mysociety.notice.Notice;
import com.mysociety.notification.Notification;
import com.mysociety.parcel.Parcel;
import com.mysociety.platform.ApplicationSettings;
import com.mysociety.platform.RoleDefinition;
import com.mysociety.resident.ResidentFlatMapping;
import com.mysociety.resident.ResidentProfile;
import com.mysociety.society.Society;
import com.mysociety.task.ActionTask;
import com.mysociety.user.*;
import com.mysociety.visitor.Visitor;
import java.util.*;

/** Explicit API projection: persistence entities are never returned by controllers. */
public final class Views {
  private Views() {}

  public static Map<String, Object> of(BaseEntity e) {
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("id", e.id);
    v.put("version", e.version);
    v.put("createdAt", e.createdAt);
    if (e instanceof TenantEntity t) v.put("societyId", t.societyId);
    if (e instanceof com.mysociety.membership.MembershipRequest x) {
      v.put("userId", x.userId);
      v.put("flatHint", x.flatHint);
      v.put("status", x.status);
      v.put("reviewNote", x.reviewNote);
      v.put("reviewedAt", x.reviewedAt);
    }
    if (e instanceof Account x) {
      v.put("name", x.name);
      v.put("email", x.email);
      v.put("mobile", x.mobile);
      v.put("status", x.status);
      v.put("superAdmin", x.superAdmin);
    }
    if (e instanceof Society x) {
      v.put("theme", x.theme);
      v.put("name", x.name);
      v.put("code", x.code);
      v.put("registrationNumber", x.registrationNumber);
      v.put("address", x.address);
      v.put("city", x.city);
      v.put("state", x.state);
      v.put("pinCode", x.pinCode);
      v.put("timezone", x.timezone);
      v.put("email", x.email);
      v.put("phone", x.phone);
      v.put("status", x.status);
      v.put("logoUrl", x.logoUrl);
      v.put("settingsJson", x.settingsJson);
    }
    if (e instanceof Membership x) {
      v.put("userId", x.userId);
      v.put("status", x.status);
      v.put("joinedAt", x.joinedAt);
      v.put("leftAt", x.leftAt);
      v.put("primaryMembership", x.primaryMembership);
      v.put("roles", x.roles);
    }
    if (e instanceof RoleDefinition x) {
      v.put("code", x.code);
      v.put("permissions", x.permissions);
    }
    if (e instanceof ApplicationSettings x) {
      v.put("applicationName", x.applicationName);
      v.put("shortName", x.shortName);
      v.put("logoUrl", x.logoUrl);
      v.put("faviconUrl", x.faviconUrl);
      v.put("footerText", x.footerText);
      v.put("supportEmail", x.supportEmail);
      v.put("supportPhone", x.supportPhone);
      v.put("updatedAt", x.updatedAt);
      v.put("updatedBy", x.updatedBy);
    }
    if (e instanceof Building x) {
      v.put("name", x.name);
      v.put("active", x.active);
    }
    if (e instanceof Wing x) {
      v.put("buildingId", x.buildingId);
      v.put("name", x.name);
      v.put("active", x.active);
    }
    if (e instanceof Floor x) {
      v.put("buildingId", x.buildingId);
      v.put("wingId", x.wingId);
      v.put("name", x.name);
      v.put("active", x.active);
    }
    if (e instanceof Flat x) {
      v.put("buildingId", x.buildingId);
      v.put("wingId", x.wingId);
      v.put("floorId", x.floorId);
      v.put("flatNumber", x.flatNumber);
      v.put("carpetArea", x.carpetArea);
      v.put("occupancyStatus", x.occupancyStatus);
      v.put("ownershipType", x.ownershipType);
      v.put("active", x.active);
    }
    if (e instanceof ResidentProfile x) {
      v.put("userId", x.userId);
      v.put("name", x.name);
      v.put("mobile", x.mobile);
      v.put("email", x.email);
      v.put("active", x.active);
    }
    if (e instanceof ResidentFlatMapping x) {
      v.put("residentId", x.residentId);
      v.put("flatId", x.flatId);
      v.put("relationshipType", x.relationshipType);
      v.put("startDate", x.startDate);
      v.put("endDate", x.endDate);
      v.put("primaryFlat", x.primaryFlat);
    }
    if (e instanceof Visitor x) {
      v.put("visitorName", x.visitorName);
      v.put("mobileNumber", x.mobileNumber);
      v.put("visitorType", x.visitorType);
      v.put("flatId", x.flatId);
      v.put("purpose", x.purpose);
      v.put("vehicleNumber", x.vehicleNumber);
      v.put("approvalStatus", x.approvalStatus);
      v.put("status", x.status);
      v.put("expectedFrom", x.expectedFrom);
      v.put("expectedUntil", x.expectedUntil);
      v.put("entryTime", x.entryTime);
      v.put("exitTime", x.exitTime);
      v.put("createdBy", x.createdBy);
      v.put("approvedBy", x.approvedBy);
    }
    if (e instanceof Parcel x) {
      v.put("flatId", x.flatId);
      v.put("courier", x.courier);
      v.put("description", x.description);
      v.put("status", x.status);
      v.put("collectedAt", x.collectedAt);
      v.put("collectedBy", x.collectedBy);
      v.put("createdBy", x.createdBy);
    }
    if (e instanceof Complaint x) {
      v.put("title", x.title);
      v.put("description", x.description);
      v.put("category", x.category);
      v.put("priority", x.priority);
      v.put("location", x.location);
      v.put("flatId", x.flatId);
      v.put("attachmentIds", x.attachmentIds);
      v.put("createdBy", x.createdBy);
      v.put("assignedTo", x.assignedTo);
      v.put("status", x.status);
    }
    if (e instanceof ComplaintHistory x) {
      v.put("complaintId", x.complaintId);
      v.put("actorId", x.actorId);
      v.put("status", x.status);
      v.put("comment", x.comment);
    }
    if (e instanceof Notice x) {
      v.put("title", x.title);
      v.put("content", x.content);
      v.put("noticeType", x.noticeType);
      v.put("audience", x.audience);
      v.put("targetIds", x.targetIds);
      v.put("publishDate", x.publishDate);
      v.put("expiryDate", x.expiryDate);
      v.put("attachmentId", x.attachmentId);
      v.put("acknowledgementRequired", x.acknowledgementRequired);
      v.put("createdBy", x.createdBy);
    }
    if (e instanceof Acknowledgement x) {
      v.put("noticeId", x.noticeId);
      v.put("userId", x.userId);
    }
    if (e instanceof Meeting x) {
      v.put("title", x.title);
      v.put("meetingType", x.meetingType);
      v.put("startsAt", x.startsAt);
      v.put("venue", x.venue);
      v.put("participantIds", x.participantIds);
      v.put("agendaJson", x.agendaJson);
      v.put("attendanceJson", x.attendanceJson);
      v.put("resolutions", x.resolutions);
      v.put("attachmentIds", x.attachmentIds);
      v.put("status", x.status);
      v.put("momStatus", x.momStatus);
      v.put("approvedBy", x.approvedBy);
      v.put("approvedAt", x.approvedAt);
      v.put("createdBy", x.createdBy);
    }
    if (e instanceof ActionTask x) {
      v.put("title", x.title);
      v.put("description", x.description);
      v.put("assignedTo", x.assignedTo);
      v.put("meetingId", x.meetingId);
      v.put("agendaItemId", x.agendaItemId);
      v.put("dueDate", x.dueDate);
      v.put("priority", x.priority);
      v.put("status", x.status);
      v.put("completedAt", x.completedAt);
      v.put("createdBy", x.createdBy);
    }
    if (e instanceof Document x) {
      v.put("fileName", x.fileName);
      v.put("contentType", x.contentType);
      v.put("size", x.size);
      v.put("visibility", x.visibility);
      v.put("uploadedBy", x.uploadedBy);
      v.put("revision", x.revision);
      v.put("previousVersionId", x.previousVersionId);
    }
    if (e instanceof Notification x) {
      v.put("userId", x.userId);
      v.put("type", x.type);
      v.put("title", x.title);
      v.put("message", x.message);
      v.put("relatedEntityType", x.relatedEntityType);
      v.put("relatedEntityId", x.relatedEntityId);
      v.put("readAt", x.readAt);
    }
    if (e instanceof EmergencyContact x) {
      v.put("name", x.name);
      v.put("category", x.category);
      v.put("phone", x.phone);
      v.put("description", x.description);
      v.put("active", x.active);
    }
    if (e instanceof AuditLog x) {
      v.put("societyId", x.societyId);
      v.put("actorUserId", x.actorUserId);
      v.put("action", x.action);
      v.put("entityType", x.entityType);
      v.put("entityId", x.entityId);
      v.put("oldValueJson", x.oldValueJson);
      v.put("newValueJson", x.newValueJson);
      v.put("correlationId", x.correlationId);
    }
    return v;
  }
}
