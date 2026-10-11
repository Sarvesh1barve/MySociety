package com.mysociety.membership;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membership_requests")
public class MembershipRequest extends TenantEntity {
  public UUID userId;

  @Column(columnDefinition = "text")
  public String flatHint;

  @Column(columnDefinition = "text")
  public String status;

  @Column(columnDefinition = "text")
  public String reviewNote;

  public UUID reviewedBy;
  public Instant reviewedAt;
}
