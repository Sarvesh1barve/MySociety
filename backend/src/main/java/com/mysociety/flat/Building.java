package com.mysociety.flat;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "buildings")
public class Building extends TenantEntity {
  @Column(columnDefinition = "text")
  public String name;

  public boolean active;
}
