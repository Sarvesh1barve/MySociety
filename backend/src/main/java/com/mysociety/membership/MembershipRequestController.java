package com.mysociety.membership;

import com.mysociety.audit.AuditService;
import com.mysociety.common.*;
import com.mysociety.flat.Flat;
import com.mysociety.platform.RoleDefinition;
import com.mysociety.resident.*;
import com.mysociety.security.*;
import com.mysociety.society.Society;
import com.mysociety.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class MembershipRequestController {
  private final Store db;
  private final Access access;
  private final AccountRepository accounts;
  private final AuditService audit;

  public MembershipRequestController(
      Store db, Access access, AccountRepository accounts, AuditService audit) {
    this.db = db;
    this.access = access;
    this.accounts = accounts;
    this.audit = audit;
  }

  public record Request(
      @NotBlank @Size(max = 30) String societyCode, @NotBlank @Size(max = 120) String flatHint) {}

  public record Review(
      @NotNull Boolean approve,
      UUID flatId,
      @Size(max = 20) String relationship,
      @Size(max = 500) String note) {}

  @GetMapping("/my-membership-requests")
  public List<Map<String, Object>> mine() {
    return db.query(MembershipRequest.class, "userId=:u", Map.of("u", access.user().id)).stream()
        .map(r -> view(r, false))
        .toList();
  }

  @PostMapping("/my-membership-requests")
  public Map<String, Object> request(@Valid @RequestBody Request body) {
    var user = accounts.lockById(access.user().id).orElseThrow(ApiException::missing);
    var societies =
        db.query(
            Society.class,
            "code=:c and status in ('ACTIVE','TRIAL')",
            Map.of("c", body.societyCode().trim().toUpperCase(Locale.ROOT)));
    if (societies.isEmpty())
      throw ApiException.bad(
          "Society code not found or access unavailable. Check the code with your administrator.");
    UUID s = societies.getFirst().id;
    if (!db.query(Membership.class, "societyId=:s and userId=:u", Map.of("s", s, "u", user.id))
        .isEmpty())
      throw ApiException.bad(
          "You already have a membership here. Contact your administrator if access is suspended or"
              + " pending.");
    var existing = db.query(MembershipRequest.class, "userId=:u", Map.of("u", user.id));
    if (existing.stream().anyMatch(r -> r.societyId.equals(s)))
      throw ApiException.bad(
          "You have already requested access to this society. Contact its administrator for help.");
    if (existing.size() >= 5)
      throw ApiException.bad(
          "Contact your administrator before requesting access to more societies.");
    var r = new MembershipRequest();
    r.societyId = s;
    r.userId = user.id;
    r.flatHint = body.flatHint().trim();
    r.status = "PENDING";
    db.save(r);
    audit.log(s, "MEMBERSHIP_REQUESTED", r, null);
    return view(r, false);
  }

  @GetMapping("/membership-requests")
  public List<Map<String, Object>> queue(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "MEMBERSHIP_MANAGE");
    return db.list(MembershipRequest.class, s).stream().map(r -> view(r, true)).toList();
  }

  @PostMapping("/membership-requests/{id}/review")
  public Map<String, Object> review(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @Valid @RequestBody Review body) {
    access.require(s, "MEMBERSHIP_MANAGE");
    var r = db.tenant(MembershipRequest.class, s, id);
    // Account lock serializes approval with other membership/identity operations.
    var u = accounts.lockById(r.userId).orElseThrow(ApiException::missing);
    if (!"PENDING".equals(r.status))
      throw ApiException.bad("This request was already reviewed. Refresh the list.");
    String before = audit.snapshot(r);
    if (body.approve()) {
      access.require(s, "RESIDENT_UPDATE");
      if (!"ACTIVE".equals(u.status)) throw ApiException.bad("This account is not active.");
      if (body.flatId() == null) throw ApiException.bad("Choose the verified resident's flat.");
      var flat = db.tenant(Flat.class, s, body.flatId());
      if (!flat.active) throw ApiException.bad("Choose an active flat.");
      String relationship = body.relationship();
      if (relationship == null
          || !Set.of("OWNER", "TENANT", "FAMILY_MEMBER").contains(relationship))
        throw ApiException.bad("Choose owner, tenant or family member.");
      String role =
          relationship.equals("OWNER")
              ? "RESIDENT_OWNER"
              : relationship.equals("TENANT") ? "RESIDENT_TENANT" : "FAMILY_MEMBER";
      var definition = db.query(RoleDefinition.class, "code=:c", Map.of("c", role)).getFirst();
      if (!access.user().superAdmin
          && !access.permissions(s).containsAll(Permissions.split(definition.permissions)))
        throw ApiException.forbidden();
      if (!db.query(Membership.class, "societyId=:s and userId=:u", Map.of("s", s, "u", u.id))
          .isEmpty())
        throw ApiException.bad("A membership already exists. Manage it from Society users.");
      var m = new Membership();
      m.societyId = s;
      m.userId = u.id;
      m.roles = role;
      m.status = "ACTIVE";
      m.joinedAt = Instant.now();
      db.save(m);
      var profiles =
          db.query(ResidentProfile.class, "societyId=:s and userId=:u", Map.of("s", s, "u", u.id));
      var p = profiles.isEmpty() ? new ResidentProfile() : profiles.getFirst();
      p.societyId = s;
      p.userId = u.id;
      p.name = u.name;
      p.email = u.email;
      p.mobile = u.mobile;
      p.active = true;
      db.save(p);
      var mapping = new ResidentFlatMapping();
      mapping.societyId = s;
      mapping.residentId = p.id;
      mapping.flatId = flat.id;
      mapping.relationshipType = relationship;
      mapping.primaryFlat = true;
      mapping.startDate = LocalDate.now(ZoneId.of(db.get(Society.class, s).timezone));
      db.save(mapping);
      r.status = "APPROVED";
    } else r.status = "REJECTED";
    r.reviewNote = body.note() == null ? null : body.note().trim();
    r.reviewedBy = access.user().id;
    r.reviewedAt = Instant.now();
    audit.log(s, "MEMBERSHIP_REQUEST_REVIEWED", r, before);
    return view(r, true);
  }

  private Map<String, Object> view(MembershipRequest r, boolean admin) {
    var v = Views.of(r);
    v.put("societyName", db.get(Society.class, r.societyId).name);
    if (admin) v.put("user", Views.of(db.get(Account.class, r.userId)));
    return v;
  }
}
