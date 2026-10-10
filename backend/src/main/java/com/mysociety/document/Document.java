package com.mysociety.document;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document extends TenantEntity {
  @Column(columnDefinition = "text")
  public String fileName;

  @Column(columnDefinition = "text")
  public String storageKey;

  @Column(columnDefinition = "text")
  public String contentType;

  public long size;

  @Column(columnDefinition = "text")
  public String visibility;

  public UUID uploadedBy;
  public int revision;
  public UUID previousVersionId;
}
