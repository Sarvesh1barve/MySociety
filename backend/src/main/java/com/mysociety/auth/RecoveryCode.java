package com.mysociety.auth;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_codes")
public class RecoveryCode extends BaseEntity {
  public UUID userId;

  @Column(columnDefinition = "text")
  public String codeHash;

  public Instant usedAt;
}
