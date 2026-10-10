package com.mysociety.user;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {
  public long tokenVersion;

  @Column(columnDefinition = "text")
  public String name;

  @Column(columnDefinition = "text")
  public String email;

  @Column(columnDefinition = "text")
  public String mobile;

  @Column(columnDefinition = "text")
  public String passwordHash;

  @Column(columnDefinition = "text")
  public String status;

  public boolean superAdmin;
}
