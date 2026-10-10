package com.mysociety.platform;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "role_definitions")
public class RoleDefinition extends BaseEntity {
  @Column(columnDefinition = "text")
  public String code;

  @Column(columnDefinition = "text")
  public String permissions;
}
