package com.mysociety.notification;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends TenantEntity {
  public UUID userId;

  @Column(columnDefinition = "text")
  public String type;

  @Column(columnDefinition = "text")
  public String title;

  @Column(columnDefinition = "text")
  public String message;

  @Column(columnDefinition = "text")
  public String relatedEntityType;

  public UUID relatedEntityId;
  public Instant readAt;
}
