package com.mysociety.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysociety.common.*;
import com.mysociety.security.Access;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final Store db;
  private final Access access;
  private final ObjectMapper json;

  public AuditService(Store db, Access access, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.json = json;
  }

  public String snapshot(BaseEntity e) {
    try {
      return json.writeValueAsString(Views.of(e));
    } catch (Exception x) {
      throw new IllegalStateException(x);
    }
  }

  public void log(UUID society, String action, BaseEntity entity, String before) {
    AuditLog a = new AuditLog();
    a.societyId = society;
    a.actorUserId = access.user().id;
    a.action = action;
    a.entityType = entity.getClass().getSimpleName();
    a.entityId = entity.id;
    a.oldValueJson = before;
    a.newValueJson = snapshot(entity);
    a.correlationId = org.slf4j.MDC.get("correlationId");
    db.save(a);
  }
}
