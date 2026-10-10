package com.mysociety.common;

import jakarta.persistence.*;
import java.util.UUID;

@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {
  @Column(nullable = false, updatable = false)
  public UUID societyId;
}
