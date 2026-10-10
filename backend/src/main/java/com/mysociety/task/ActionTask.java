package com.mysociety.task;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "action_tasks")
public class ActionTask extends TenantEntity {
  @Column(columnDefinition = "text")
  public String title;

  @Column(columnDefinition = "text")
  public String description;

  public UUID assignedTo;
  public UUID meetingId;

  @Column(columnDefinition = "text")
  public String agendaItemId;

  public LocalDate dueDate;

  @Column(columnDefinition = "text")
  public String priority;

  @Column(columnDefinition = "text")
  public String status;

  public Instant completedAt;
  public UUID createdBy;
}
