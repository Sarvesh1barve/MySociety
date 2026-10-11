package com.mysociety.user;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  @Query("select a from Account a where lower(a.email)=:login or a.mobile=:login")
  Optional<Account> findByLogin(@Param("login") String login);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where lower(a.email)=:login or a.mobile=:login")
  Optional<Account> lockByLogin(@Param("login") String login);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id=:id")
  Optional<Account> lockById(@Param("id") UUID id);
}
