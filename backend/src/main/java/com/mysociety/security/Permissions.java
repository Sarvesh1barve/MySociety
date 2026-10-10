package com.mysociety.security;

import java.util.*;

public final class Permissions {
  public static final Set<String> ALL =
      Set.of(
          "SOCIETY_READ",
          "SOCIETY_UPDATE",
          "RESIDENT_READ",
          "RESIDENT_CREATE",
          "RESIDENT_UPDATE",
          "RESIDENT_READ_ALL",
          "FLAT_READ",
          "FLAT_CREATE",
          "FLAT_UPDATE",
          "USER_MANAGE",
          "MEMBERSHIP_MANAGE",
          "VISITOR_READ",
          "VISITOR_CREATE",
          "VISITOR_APPROVE",
          "VISITOR_CHECKIN",
          "VISITOR_CHECKOUT",
          "VISITOR_READ_ALL",
          "PARCEL_READ",
          "PARCEL_CREATE",
          "PARCEL_COLLECT",
          "PARCEL_READ_ALL",
          "COMPLAINT_CREATE",
          "COMPLAINT_READ",
          "COMPLAINT_ASSIGN",
          "COMPLAINT_UPDATE",
          "COMPLAINT_READ_ALL",
          "NOTICE_CREATE",
          "NOTICE_READ",
          "NOTICE_ACKNOWLEDGE",
          "MEETING_CREATE",
          "MEETING_EDIT",
          "MEETING_READ",
          "MEETING_READ_ALL",
          "MOM_CREATE",
          "MOM_APPROVE",
          "TASK_READ",
          "TASK_CREATE",
          "TASK_UPDATE",
          "TASK_READ_ALL",
          "DOCUMENT_UPLOAD",
          "DOCUMENT_READ",
          "DOCUMENT_COMMITTEE",
          "EMERGENCY_CONTACT_MANAGE",
          "EMERGENCY_READ",
          "REPORT_VIEW",
          "AUDIT_READ",
          "APPLICATION_SETTING_MANAGE",
          "SOCIETY_CREATE",
          "SOCIETY_MANAGE");
  public static final Set<String> PLATFORM =
      Set.of("APPLICATION_SETTING_MANAGE", "SOCIETY_CREATE", "SOCIETY_MANAGE");

  public static Set<String> split(String value) {
    return value == null || value.isBlank()
        ? Set.of()
        : new HashSet<>(Arrays.asList(value.split(",")));
  }

  public static Map<String, Set<String>> defaults() {
    Set<String> resident =
        Set.of(
            "SOCIETY_READ",
            "FLAT_READ",
            "VISITOR_READ",
            "VISITOR_CREATE",
            "VISITOR_APPROVE",
            "PARCEL_READ",
            "PARCEL_COLLECT",
            "COMPLAINT_CREATE",
            "COMPLAINT_READ",
            "NOTICE_READ",
            "NOTICE_ACKNOWLEDGE",
            "MEETING_READ",
            "DOCUMENT_READ",
            "EMERGENCY_READ",
            "TASK_READ",
            "TASK_UPDATE");
    Set<String> committee = new HashSet<>(ALL);
    committee.removeAll(PLATFORM);
    committee.remove("MEMBERSHIP_MANAGE");
    committee.remove("USER_MANAGE");
    Set<String> admin = new HashSet<>(committee);
    admin.addAll(Set.of("MEMBERSHIP_MANAGE", "USER_MANAGE"));
    return Map.ofEntries(
        Map.entry("CHAIRMAN", admin),
        Map.entry("SECRETARY", admin),
        Map.entry("SOCIETY_MANAGER", admin),
        Map.entry("COMMITTEE_MEMBER", committee),
        Map.entry("TREASURER", committee),
        Map.entry("RESIDENT_OWNER", resident),
        Map.entry("RESIDENT_TENANT", resident),
        Map.entry("FAMILY_MEMBER", resident),
        Map.entry(
            "WATCHMAN",
            Set.of(
                "SOCIETY_READ",
                "FLAT_READ",
                "VISITOR_READ",
                "VISITOR_CREATE",
                "VISITOR_CHECKIN",
                "VISITOR_CHECKOUT",
                "VISITOR_READ_ALL",
                "PARCEL_READ",
                "PARCEL_CREATE",
                "PARCEL_COLLECT",
                "PARCEL_READ_ALL",
                "EMERGENCY_READ",
                "NOTICE_READ")),
        Map.entry(
            "AUDITOR",
            Set.of(
                "SOCIETY_READ",
                "FLAT_READ",
                "REPORT_VIEW",
                "AUDIT_READ",
                "MEETING_READ",
                "MEETING_READ_ALL",
                "DOCUMENT_READ",
                "DOCUMENT_COMMITTEE",
                "NOTICE_READ",
                "EMERGENCY_READ")));
  }
}
