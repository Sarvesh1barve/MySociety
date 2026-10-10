package com.mysociety.flat;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "floors")
public class Floor extends TenantEntity {
  public UUID buildingId;
  public UUID wingId;

  @Column(columnDefinition = "text")
  public String name;

  public boolean active;
}
