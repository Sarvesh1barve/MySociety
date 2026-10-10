package com.mysociety.common;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class Store {
  @PersistenceContext private EntityManager em;

  public <T extends BaseEntity> T save(T entity) {
    if (em.contains(entity)) return entity;
    em.persist(entity);
    return entity;
  }

  public <T extends BaseEntity> T get(Class<T> type, UUID id) {
    T v = em.find(type, id);
    if (v == null) throw ApiException.missing();
    return v;
  }

  public <T extends TenantEntity> T tenant(Class<T> type, UUID society, UUID id) {
    T v = get(type, id);
    if (!v.societyId.equals(society)) throw ApiException.forbidden();
    return v;
  }

  public <T extends BaseEntity> List<T> all(Class<T> type) {
    return em.createQuery("from " + type.getSimpleName() + " order by createdAt desc", type)
        .getResultList();
  }

  public <T extends TenantEntity> List<T> list(Class<T> type, UUID society) {
    return em.createQuery(
            "from " + type.getSimpleName() + " where societyId=:s order by createdAt desc", type)
        .setParameter("s", society)
        .getResultList();
  }

  public <T extends BaseEntity> List<T> query(
      Class<T> type, String clause, Map<String, Object> args) {
    var q = em.createQuery("from " + type.getSimpleName() + " where " + clause, type);
    args.forEach(q::setParameter);
    return q.getResultList();
  }

  public void flush() {
    em.flush();
  }
}
