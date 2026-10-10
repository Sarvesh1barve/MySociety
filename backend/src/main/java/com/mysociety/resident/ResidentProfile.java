package com.mysociety.resident;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "resident_profiles")
public class ResidentProfile extends TenantEntity {
  public UUID userId;

  @Column(columnDefinition = "text")
  public String name;

  @Column(columnDefinition = "text")
  public String mobile;

  @Column(columnDefinition = "text")
  public String email;

  public boolean active;
}
