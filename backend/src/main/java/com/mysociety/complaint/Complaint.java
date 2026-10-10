package com.mysociety.complaint;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "complaints")
public class Complaint extends TenantEntity {
  @Column(columnDefinition = "text")
  public String title;

  @Column(columnDefinition = "text")
  public String description;

  @Column(columnDefinition = "text")
  public String category;

  @Column(columnDefinition = "text")
  public String priority;

  @Column(columnDefinition = "text")
  public String location;

  public UUID flatId;

  @Column(columnDefinition = "text")
  public String attachmentIds;

  public UUID createdBy;
  public UUID assignedTo;

  @Column(columnDefinition = "text")
  public String status;
}
