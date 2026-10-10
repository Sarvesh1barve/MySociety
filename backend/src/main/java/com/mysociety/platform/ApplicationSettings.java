package com.mysociety.platform;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "application_settings")
public class ApplicationSettings extends BaseEntity {
  @Column(columnDefinition = "text")
  public String applicationName;

  @Column(columnDefinition = "text")
  public String shortName;

  @Column(columnDefinition = "text")
  public String logoUrl;

  @Column(columnDefinition = "text")
  public String faviconUrl;

  @Column(columnDefinition = "text")
  public String footerText;

  @Column(columnDefinition = "text")
  public String supportEmail;

  @Column(columnDefinition = "text")
  public String supportPhone;

  public Instant updatedAt;
  public UUID updatedBy;
}
