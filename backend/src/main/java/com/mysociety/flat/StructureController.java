package com.mysociety.flat;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.membership.*;
import com.mysociety.platform.*;
import com.mysociety.resident.*;
import com.mysociety.security.*;
import com.mysociety.society.*;
import com.mysociety.user.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class StructureController {
  private final Store db;
  private final Access access;
  private final AuditService audit;
  private final PlatformController platform;

  public StructureController(
      Store db, Access access, AuditService audit, PlatformController platform) {
    this.db = db;
    this.access = access;
    this.audit = audit;
    this.platform = platform;
  }

  @GetMapping("/society")
  public Map<String, Object> society(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "SOCIETY_READ");
    return Views.of(db.get(Society.class, s));
  }

  @PutMapping("/society")
  public Map<String, Object> updateSociety(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "SOCIETY_UPDATE");
    Society v = db.get(Society.class, s);
    String before = audit.snapshot(v);
    Input i = new Input(b);
    v.name = i.required("name", 160);
    v.address = i.text("address", 500);
    v.email = i.email("email");
    v.phone = i.mobile("phone", false);
    v.timezone = i.value("timezone", "Asia/Kolkata");
    try {
      ZoneId.of(v.timezone);
    } catch (Exception e) {
      throw ApiException.bad("Invalid timezone.");
    }
    v.settingsJson = i.text("settingsJson", 4000);
    if (v.settingsJson != null) {
      try {
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(v.settingsJson);
      } catch (Exception e) {
        throw ApiException.bad("Settings must be valid JSON.");
      }
    }
    audit.log(s, "SOCIETY_UPDATED", v, before);
    return Views.of(v);
  }

  @GetMapping("/buildings")
  public List<Map<String, Object>> buildings(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "FLAT_READ");
    return db.list(Building.class, s).stream().map(Views::of).toList();
  }

  @PostMapping("/buildings")
  public Map<String, Object> building(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "FLAT_CREATE");
    Building v = new Building();
    v.societyId = s;
    v.name = new Input(b).required("name", 100);
    v.active = true;
    db.save(v);
    audit.log(s, "BUILDING_CREATED", v, null);
    return Views.of(v);
  }

  @GetMapping("/wings")
  public List<Map<String, Object>> wings(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "FLAT_READ");
    return db.list(Wing.class, s).stream().map(Views::of).toList();
  }

  @PostMapping("/wings")
  public Map<String, Object> wing(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "FLAT_CREATE");
    Input i = new Input(b);
    Wing v = new Wing();
    v.societyId = s;
    v.name = i.required("name", 100);
    v.buildingId = i.id("buildingId");
    ref(Building.class, s, v.buildingId);
    v.active = true;
    db.save(v);
    audit.log(s, "WING_CREATED", v, null);
    return Views.of(v);
  }

  @GetMapping("/floors")
  public List<Map<String, Object>> floors(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "FLAT_READ");
    return db.list(Floor.class, s).stream().map(Views::of).toList();
  }

  @PostMapping("/floors")
  public Map<String, Object> floor(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "FLAT_CREATE");
    Input i = new Input(b);
    Floor v = new Floor();
    v.societyId = s;
    v.name = i.required("name", 100);
    v.buildingId = i.id("buildingId");
    v.wingId = i.id("wingId");
    ref(Building.class, s, v.buildingId);
    ref(Wing.class, s, v.wingId);
    if (v.wingId != null) {
      Wing w = db.tenant(Wing.class, s, v.wingId);
      if (v.buildingId != null && !Objects.equals(v.buildingId, w.buildingId))
        throw ApiException.bad("Wing does not belong to building.");
      v.buildingId = w.buildingId;
    }
    v.active = true;
    db.save(v);
    audit.log(s, "FLOOR_CREATED", v, null);
    return Views.of(v);
  }

  @GetMapping("/flats")
  public List<Map<String, Object>> flats(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "FLAT_READ");
    return db.list(Flat.class, s).stream().map(Views::of).toList();
  }

  @PostMapping("/flats")
  public Map<String, Object> flat(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "FLAT_CREATE");
    Flat v = new Flat();
    v.societyId = s;
    applyFlat(v, new Input(b));
    db.save(v);
    audit.log(s, "FLAT_CREATED", v, null);
    return Views.of(v);
  }

  @PutMapping("/flats/{id}")
  public Map<String, Object> flatEdit(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "FLAT_UPDATE");
    Flat v = db.tenant(Flat.class, s, id);
    String before = audit.snapshot(v);
    applyFlat(v, new Input(b));
    audit.log(s, "FLAT_UPDATED", v, before);
    return Views.of(v);
  }

  private void applyFlat(Flat v, Input i) {
    UUID s = v.societyId;
    v.flatNumber = i.required("flatNumber", 30);
    v.buildingId = i.id("buildingId");
    v.wingId = i.id("wingId");
    v.floorId = i.id("floorId");
    ref(Building.class, s, v.buildingId);
    ref(Wing.class, s, v.wingId);
    ref(Floor.class, s, v.floorId);
    if (v.floorId != null) {
      Floor f = db.tenant(Floor.class, s, v.floorId);
      if (v.wingId != null && !Objects.equals(f.wingId, v.wingId))
        throw ApiException.bad("Floor/wing mismatch.");
      if (v.buildingId != null && !Objects.equals(f.buildingId, v.buildingId))
        throw ApiException.bad("Floor/building mismatch.");
      v.wingId = f.wingId;
      v.buildingId = f.buildingId;
    }
    if (v.wingId != null) {
      Wing w = db.tenant(Wing.class, s, v.wingId);
      if (v.buildingId != null && !Objects.equals(w.buildingId, v.buildingId))
        throw ApiException.bad("Wing/building mismatch.");
      v.buildingId = w.buildingId;
    }
    v.carpetArea = i.decimal("carpetArea");
    v.occupancyStatus =
        i.choice(
            "occupancyStatus",
            "VACANT",
            "OWNER_OCCUPIED",
            "TENANT_OCCUPIED",
            "VACANT",
            "LOCKED",
            "COMMERCIAL");
    v.ownershipType =
        i.choice("ownershipType", "INDIVIDUAL", "INDIVIDUAL", "JOINT", "SOCIETY", "COMPANY");
    v.active = i.bool("active", true);
  }

  private <T extends TenantEntity> void ref(Class<T> t, UUID s, UUID id) {
    if (id != null) db.tenant(t, s, id);
  }

  @GetMapping("/residents")
  public List<Map<String, Object>> residents(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "RESIDENT_READ");
    boolean all = access.can(s, "RESIDENT_READ_ALL");
    return db.list(ResidentProfile.class, s).stream()
        .filter(v -> all || access.user().id.equals(v.userId))
        .map(
            v -> {
              var m = Views.of(v);
              m.put(
                  "mappings",
                  db.list(ResidentFlatMapping.class, s).stream()
                      .filter(x -> x.residentId.equals(v.id))
                      .map(Views::of)
                      .toList());
              return m;
            })
        .toList();
  }

  @PostMapping("/residents")
  public Map<String, Object> resident(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "RESIDENT_CREATE");
    Input i = new Input(b);
    ResidentProfile v = new ResidentProfile();
    v.societyId = s;
    v.userId = i.id("userId");
    access.activeUserInSociety(s, v.userId);
    v.name = i.required("name", 120);
    v.mobile = i.mobile("mobile", false);
    v.email = i.email("email");
    v.active = true;
    db.save(v);
    audit.log(s, "RESIDENT_CREATED", v, null);
    return Views.of(v);
  }

  @PutMapping("/residents/{id}")
  public Map<String, Object> residentEdit(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "RESIDENT_UPDATE");
    ResidentProfile v = db.tenant(ResidentProfile.class, s, id);
    String before = audit.snapshot(v);
    Input i = new Input(b);
    v.name = i.required("name", 120);
    v.mobile = i.mobile("mobile", false);
    v.email = i.email("email");
    v.active = i.bool("active", true);
    audit.log(s, "RESIDENT_UPDATED", v, before);
    return Views.of(v);
  }

  @GetMapping("/resident-flat-mappings")
  public List<Map<String, Object>> mappings(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "FLAT_READ");
    Set<UUID> own = access.flats(s);
    boolean all = access.can(s, "RESIDENT_READ_ALL");
    return db.list(ResidentFlatMapping.class, s).stream()
        .filter(
            m ->
                all
                    || (own.contains(m.flatId)
                        && access
                            .user()
                            .id
                            .equals(db.tenant(ResidentProfile.class, s, m.residentId).userId)))
        .map(Views::of)
        .toList();
  }

  @PostMapping("/resident-flat-mappings")
  public Map<String, Object> mapFlat(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "RESIDENT_UPDATE");
    Input i = new Input(b);
    ResidentFlatMapping v = new ResidentFlatMapping();
    v.societyId = s;
    v.residentId = i.requiredId("residentId");
    v.flatId = i.requiredId("flatId");
    db.tenant(ResidentProfile.class, s, v.residentId);
    db.tenant(Flat.class, s, v.flatId);
    v.relationshipType = i.choice("relationshipType", "OWNER", "OWNER", "TENANT", "FAMILY_MEMBER");
    v.startDate = i.date("startDate");
    v.endDate = i.date("endDate");
    if (v.startDate != null && v.endDate != null && v.endDate.isBefore(v.startDate))
      throw ApiException.bad("End date cannot precede start date.");
    v.primaryFlat = i.bool("primaryFlat", true);
    db.save(v);
    audit.log(s, "RESIDENT_FLAT_LINKED", v, null);
    return Views.of(v);
  }

  @PatchMapping("/resident-flat-mappings/{id}/end")
  public Map<String, Object> endMapping(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "RESIDENT_UPDATE");
    var m = db.tenant(ResidentFlatMapping.class, s, id);
    String before = audit.snapshot(m);
    LocalDate today = LocalDate.now(ZoneId.of(db.get(Society.class, s).timezone));
    m.endDate = m.startDate != null && m.startDate.isAfter(today) ? m.startDate : today;
    audit.log(s, "RESIDENT_FLAT_UNLINKED", m, before);
    return Views.of(m);
  }

  @GetMapping("/society-users")
  public List<Map<String, Object>> users(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "RESIDENT_READ_ALL");
    return db.list(Membership.class, s).stream()
        .map(
            m -> {
              var v = Views.of(m);
              v.put("user", Views.of(db.get(Account.class, m.userId)));
              return v;
            })
        .toList();
  }

  @PostMapping("/society-users")
  public Map<String, Object> createUser(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "USER_MANAGE");
    Input i = new Input(b);
    Account u = platform.newUser(i);
    Membership m = new Membership();
    m.societyId = s;
    m.userId = u.id;
    applyMembership(m, i);
    db.save(m);
    audit.log(s, "USER_ADDED_TO_SOCIETY", m, null);
    return Map.of("user", Views.of(u), "membership", Views.of(m));
  }

  @PostMapping("/society-memberships")
  public Map<String, Object> addMembership(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    // Linking an existing global identity is a platform-owner operation to prevent account
    // enumeration.
    access.platform();
    Input i = new Input(b);
    UUID uid = i.requiredId("userId");
    db.get(Account.class, uid);
    Membership m = new Membership();
    m.societyId = s;
    m.userId = uid;
    db.get(Society.class, s);
    applyMembership(m, i);
    db.save(m);
    audit.log(s, "USER_ADDED_TO_SOCIETY", m, null);
    return Views.of(m);
  }

  @PutMapping("/society-memberships/{id}")
  public Map<String, Object> updateMembership(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "MEMBERSHIP_MANAGE");
    Membership m = db.tenant(Membership.class, s, id);
    if (m.userId.equals(access.user().id) && !access.user().superAdmin)
      throw ApiException.bad("Ask another administrator to change your membership.");
    String before = audit.snapshot(m);
    applyMembership(m, new Input(b));
    audit.log(s, "MEMBERSHIP_UPDATED", m, before);
    return Views.of(m);
  }

  private void applyMembership(Membership m, Input i) {
    m.roles = i.required("roles", 500);
    Set<String> roles = Permissions.split(m.roles);
    Set<String> known = new HashSet<>();
    db.all(RoleDefinition.class).forEach(r -> known.add(r.code));
    if (!known.containsAll(roles)) throw ApiException.bad("Unknown society role.");
    if (!access.user().superAdmin) {
      Set<String> granted = new HashSet<>();
      db.all(RoleDefinition.class).stream()
          .filter(r -> roles.contains(r.code))
          .forEach(r -> granted.addAll(Permissions.split(r.permissions)));
      if (!access.permissions(m.societyId).containsAll(granted)) throw ApiException.forbidden();
    }
    m.status = i.choice("status", "ACTIVE", "PENDING", "ACTIVE", "SUSPENDED", "INACTIVE", "LEFT");
    m.primaryMembership = i.bool("primaryMembership", false);
    if (m.joinedAt == null) m.joinedAt = Instant.now();
    m.leftAt = "LEFT".equals(m.status) ? Instant.now() : null;
  }
}
