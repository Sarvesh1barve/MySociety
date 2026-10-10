package com.mysociety.flat;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "wings")
public class Wing extends TenantEntity {
  public UUID buildingId;

  @Column(columnDefinition = "text")
  public String name;

  public boolean active;
}
