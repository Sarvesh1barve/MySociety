package com.mysociety.visitor;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "visitors")
public class Visitor extends TenantEntity {
  @Column(columnDefinition = "text")
  public String visitorName;

  @Column(columnDefinition = "text")
  public String mobileNumber;

  @Column(columnDefinition = "text")
  public String visitorType;

  public UUID flatId;

  @Column(columnDefinition = "text")
  public String purpose;

  @Column(columnDefinition = "text")
  public String vehicleNumber;

  @Column(columnDefinition = "text")
  public String approvalStatus;

  @Column(columnDefinition = "text")
  public String status;

  public Instant expectedFrom;
  public Instant expectedUntil;
  public Instant entryTime;
  public Instant exitTime;
  public UUID createdBy;
  public UUID approvedBy;
}
