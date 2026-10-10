package com.mysociety.emergency;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "emergency_contacts")
public class EmergencyContact extends TenantEntity {
  @Column(columnDefinition = "text")
  public String name;

  @Column(columnDefinition = "text")
  public String category;

  @Column(columnDefinition = "text")
  public String phone;

  @Column(columnDefinition = "text")
  public String description;

  public boolean active;
}
