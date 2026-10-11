package com.mysociety.society;

import com.mysociety.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "societies")
public class Society extends BaseEntity {
  @Column(columnDefinition = "text")
  public String theme = "FOREST";

  @Column(columnDefinition = "text")
  public String name;

  @Column(columnDefinition = "text")
  public String code;

  @Column(columnDefinition = "text")
  public String registrationNumber;

  @Column(columnDefinition = "text")
  public String address;

  @Column(columnDefinition = "text")
  public String city;

  @Column(columnDefinition = "text")
  public String state;

  @Column(columnDefinition = "text")
  public String pinCode;

  @Column(columnDefinition = "text")
  public String timezone;

  @Column(columnDefinition = "text")
  public String email;

  @Column(columnDefinition = "text")
  public String phone;

  @Column(columnDefinition = "text")
  public String status;

  @Column(columnDefinition = "text")
  public String logoUrl;

  @Column(columnDefinition = "text")
  public String settingsJson;
}
