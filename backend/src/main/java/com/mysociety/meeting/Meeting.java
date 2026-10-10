package com.mysociety.meeting;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class Meeting extends TenantEntity {
  @Column(columnDefinition = "text")
  public String title;

  @Column(columnDefinition = "text")
  public String meetingType;

  public Instant startsAt;

  @Column(columnDefinition = "text")
  public String venue;

  @Column(columnDefinition = "text")
  public String participantIds;

  @Column(columnDefinition = "text")
  public String agendaJson;

  @Column(columnDefinition = "text")
  public String attendanceJson;

  @Column(columnDefinition = "text")
  public String resolutions;

  @Column(columnDefinition = "text")
  public String attachmentIds;

  @Column(columnDefinition = "text")
  public String status;

  @Column(columnDefinition = "text")
  public String momStatus;

  public UUID approvedBy;
  public Instant approvedAt;
  public UUID createdBy;
}
