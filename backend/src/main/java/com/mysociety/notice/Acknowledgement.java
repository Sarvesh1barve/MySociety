package com.mysociety.notice;

import com.mysociety.common.TenantEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "acknowledgements")
public class Acknowledgement extends TenantEntity {
  public UUID noticeId;
  public UUID userId;
}
