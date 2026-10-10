package com.mysociety.security;

import com.mysociety.common.*;
import com.mysociety.membership.Membership;
import com.mysociety.platform.RoleDefinition;
import com.mysociety.resident.*;
import com.mysociety.society.Society;
import com.mysociety.user.Account;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class Access {
  private final Store db;

  public Access(Store db) {
    this.db = db;
  }

  public Account user() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null)
      throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Please sign in.");
    Account a = db.get(Account.class, UUID.fromString(auth.getName()));
    if (auth
        instanceof
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
                jwt) {
      Number revision = jwt.getToken().getClaim("revision");
      if (revision == null || revision.longValue() != a.tokenVersion)
        throw new ApiException(
            org.springframework.http.HttpStatus.UNAUTHORIZED,
            "Your session has expired. Please sign in.");
    }
    if (!"ACTIVE".equals(a.status)) throw ApiException.forbidden();
    return a;
  }

  public void platform() {
    if (!user().superAdmin) throw ApiException.forbidden();
  }

  public Membership membership(UUID society, UUID user) {
    return db
        .query(
            Membership.class,
            "societyId=:s and userId=:u and status='ACTIVE'",
            Map.of("s", society, "u", user))
        .stream()
        .findFirst()
        .orElseThrow(ApiException::forbidden);
  }

  public Set<String> permissions(UUID society) {
    Account u = user();
    Society s = db.get(Society.class, society);
    if (u.superAdmin) return Permissions.ALL;
    Membership m = membership(society, u.id);
    if (!Set.of("ACTIVE", "TRIAL").contains(s.status)) throw ApiException.forbidden();
    Set<String> roles = Permissions.split(m.roles), result = new HashSet<>();
    db.all(RoleDefinition.class).stream()
        .filter(r -> roles.contains(r.code))
        .forEach(r -> result.addAll(Permissions.split(r.permissions)));
    result.removeAll(Permissions.PLATFORM);
    return result;
  }

  public void require(UUID society, String permission) {
    if (!permissions(society).contains(permission)) throw ApiException.forbidden();
  }

  public boolean can(UUID society, String permission) {
    return permissions(society).contains(permission);
  }

  public Set<UUID> flats(UUID society) {
    UUID uid = user().id;
    LocalDate today = LocalDate.now(java.time.ZoneId.of(db.get(Society.class, society).timezone));
    Set<UUID> residents = new HashSet<>();
    db.list(ResidentProfile.class, society).stream()
        .filter(r -> uid.equals(r.userId) && r.active)
        .forEach(r -> residents.add(r.id));
    Set<UUID> flats = new HashSet<>();
    db.list(ResidentFlatMapping.class, society).stream()
        .filter(
            m ->
                residents.contains(m.residentId)
                    && (m.startDate == null || !m.startDate.isAfter(today))
                    && (m.endDate == null || m.endDate.isAfter(today)))
        .forEach(m -> flats.add(m.flatId));
    return flats;
  }

  public void ownFlat(UUID society, UUID flat) {
    if (flat == null || !flats(society).contains(flat)) throw ApiException.forbidden();
  }

  public void activeUserInSociety(UUID society, UUID userId) {
    if (userId != null) {
      membership(society, userId);
      if (!"ACTIVE".equals(db.get(Account.class, userId).status))
        throw ApiException.bad("Assignee must be active.");
    }
  }
}
