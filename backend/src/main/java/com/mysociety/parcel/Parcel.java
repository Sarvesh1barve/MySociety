package com.mysociety.parcel;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "parcels")
public class Parcel extends TenantEntity {
  public UUID flatId;

  @Column(columnDefinition = "text")
  public String courier;

  @Column(columnDefinition = "text")
  public String description;

  @Column(columnDefinition = "text")
  public String status;

  public Instant collectedAt;
  public UUID collectedBy;
  public UUID createdBy;
}
