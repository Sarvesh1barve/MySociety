package com.mysociety.membership;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership extends TenantEntity {
  public UUID userId;

  @Column(columnDefinition = "text")
  public String status;

  public Instant joinedAt;
  public Instant leftAt;
  public boolean primaryMembership;

  @Column(columnDefinition = "text")
  public String roles;
}
