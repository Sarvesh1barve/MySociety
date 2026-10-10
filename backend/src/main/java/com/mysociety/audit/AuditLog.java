package com.mysociety.audit;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {
  public UUID societyId;
  public UUID actorUserId;

  @Column(columnDefinition = "text")
  public String action;

  @Column(columnDefinition = "text")
  public String entityType;

  public UUID entityId;

  @Column(columnDefinition = "text")
  public String oldValueJson;

  @Column(columnDefinition = "text")
  public String newValueJson;

  @Column(columnDefinition = "text")
  public String correlationId;
}
