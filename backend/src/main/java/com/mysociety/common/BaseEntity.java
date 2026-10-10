package com.mysociety.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
  @Id public UUID id = UUID.randomUUID();
  @Version public long version;

  @Column(nullable = false, updatable = false)
  public Instant createdAt = Instant.now();
}
