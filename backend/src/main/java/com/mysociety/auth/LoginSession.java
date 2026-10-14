package com.mysociety.auth;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_sessions")
public class LoginSession extends BaseEntity {
  public UUID userId;
  public long tokenVersion;
  public String tokenHash;
  public String previousHash;
  public Instant rotatedAt;
  public Instant expiresAt;
  public Instant idleExpiresAt;
  public boolean revoked;
}
