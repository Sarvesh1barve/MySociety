package com.mysociety.resident;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "resident_flat_mappings")
public class ResidentFlatMapping extends TenantEntity {
  public UUID residentId;
  public UUID flatId;

  @Column(columnDefinition = "text")
  public String relationshipType;

  public LocalDate startDate;
  public LocalDate endDate;
  public boolean primaryFlat;
}
