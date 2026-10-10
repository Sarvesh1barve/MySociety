package com.mysociety.notice;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "notices")
public class Notice extends TenantEntity {
  @Column(columnDefinition = "text")
  public String title;

  @Column(columnDefinition = "text")
  public String content;

  @Column(columnDefinition = "text")
  public String noticeType;

  @Column(columnDefinition = "text")
  public String audience;

  @Column(columnDefinition = "text")
  public String targetIds;

  public Instant publishDate;
  public Instant expiryDate;
  public UUID attachmentId;
  public boolean acknowledgementRequired;
  public UUID createdBy;
}
