package com.mysociety.complaint;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "complaint_history")
public class ComplaintHistory extends TenantEntity {
  public UUID complaintId;
  public UUID actorId;

  @Column(columnDefinition = "text")
  public String status;

  @Column(columnDefinition = "text")
  public String comment;
}
