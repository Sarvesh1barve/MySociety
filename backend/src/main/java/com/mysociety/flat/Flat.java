package com.mysociety.flat;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "flats")
public class Flat extends TenantEntity {
  public UUID buildingId;
  public UUID wingId;
  public UUID floorId;

  @Column(columnDefinition = "text")
  public String flatNumber;

  public BigDecimal carpetArea;

  @Column(columnDefinition = "text")
  public String occupancyStatus;

  @Column(columnDefinition = "text")
  public String ownershipType;

  public boolean active;
}
