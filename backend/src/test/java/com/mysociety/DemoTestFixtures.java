package com.mysociety;

import com.mysociety.common.*;
import com.mysociety.complaint.*;
import com.mysociety.emergency.*;
import com.mysociety.flat.*;
import com.mysociety.meeting.*;
import com.mysociety.membership.*;
import com.mysociety.notice.*;
import com.mysociety.parcel.*;
import com.mysociety.platform.*;
import com.mysociety.resident.*;
import com.mysociety.security.*;
import com.mysociety.society.*;
import com.mysociety.task.*;
import com.mysociety.user.*;
import com.mysociety.visitor.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.boot.test.context.TestComponent
@org.springframework.core.annotation.Order(100)
public class DemoTestFixtures implements ApplicationRunner {
  private final Store db;
  private final Environment env;
  private final PasswordEncoder passwords;

  public DemoTestFixtures(Store db, Environment env, PasswordEncoder passwords) {
    this.db = db;
    this.env = env;
    this.passwords = passwords;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.all(RoleDefinition.class).isEmpty())
      Permissions.defaults()
          .forEach(
              (code, perms) -> {
                RoleDefinition r = new RoleDefinition();
                r.code = code;
                r.permissions = String.join(",", new TreeSet<>(perms));
                db.save(r);
              });
    if (db.all(ApplicationSettings.class).isEmpty()) {
      ApplicationSettings a = new ApplicationSettings();
      a.applicationName = "My Society";
      a.shortName = "My Society";
      a.footerText = "A little closer. A better community.";
      a.updatedAt = Instant.now();
      db.save(a);
    }
    boolean demo = true;
    if (db.all(Account.class).stream().noneMatch(a -> a.superAdmin)) {
      String email = env.getProperty("INITIAL_SUPER_ADMIN_EMAIL"),
          mobile = env.getProperty("INITIAL_SUPER_ADMIN_MOBILE"),
          password = env.getProperty("INITIAL_SUPER_ADMIN_PASSWORD");
      if (password == null
          || password.length() < 12
          || (email == null || email.isBlank()) && (mobile == null || mobile.isBlank()))
        throw new IllegalStateException(
            "Set INITIAL_SUPER_ADMIN_EMAIL or MOBILE and a password of at least 12 characters.");
      Account owner =
          account(
              env.getProperty("INITIAL_SUPER_ADMIN_NAME", "Platform Owner"),
              email,
              mobile,
              password);
      owner.superAdmin = true;
    }
    if (demo && db.all(Society.class).isEmpty()) {
      String password = env.getProperty("DEMO_PASSWORD");
      if (password == null || password.length() < 12)
        throw new IllegalStateException(
            "The dev profile requires DEMO_PASSWORD of at least 12 characters.");
      Society a = society("My Society Demo CHS", "DEMO"),
          b = society("Sai Darshan Demo CHS", "SAI");
      Account secretary =
          account("Ananya Deshmukh", "secretary@demo.local", "9000000001", password);
      Account watchman = account("Ramesh Patil", "watchman@demo.local", "9000000002", password);
      Account resident = account("Aarav Shah", "resident@demo.local", "9000000003", password);
      Account other = account("Meera Joshi", "resident2@demo.local", "9000000004", password);
      member(a, secretary, "SECRETARY,RESIDENT_OWNER");
      member(b, secretary, "RESIDENT_OWNER");
      member(a, watchman, "WATCHMAN");
      member(a, resident, "RESIDENT_OWNER");
      member(b, other, "RESIDENT_OWNER");
      Building building = new Building();
      building.societyId = a.id;
      building.name = "Main building";
      building.active = true;
      db.save(building);
      List<Flat> flats = new ArrayList<>();
      for (String wn : List.of("A", "B")) {
        Wing w = new Wing();
        w.societyId = a.id;
        w.buildingId = building.id;
        w.name = wn;
        w.active = true;
        db.save(w);
        for (int floor = 1; floor <= (wn.equals("A") ? 2 : 1); floor++) {
          Floor f = new Floor();
          f.societyId = a.id;
          f.buildingId = building.id;
          f.wingId = w.id;
          f.name = "" + floor;
          f.active = true;
          db.save(f);
          for (int n = 1; n <= 2; n++) {
            Flat flat = flat(a, wn + "-" + floor + "0" + n);
            flat.buildingId = building.id;
            flat.wingId = w.id;
            flat.floorId = f.id;
            flats.add(flat);
          }
        }
      }
      Flat bf = flat(b, "C-704");
      profile(a, resident, flats.getFirst());
      profile(a, secretary, flats.get(1));
      profile(b, other, bf);
      profile(b, secretary, bf);
      Visitor v = new Visitor();
      v.societyId = a.id;
      v.flatId = flats.getFirst().id;
      v.visitorName = "Vikram Shah";
      v.mobileNumber = "9000000010";
      v.visitorType = "GUEST";
      v.purpose = "Family visit";
      v.status = "WAITING_APPROVAL";
      v.approvalStatus = "PENDING";
      v.createdBy = watchman.id;
      db.save(v);
      Parcel p = new Parcel();
      p.societyId = a.id;
      p.flatId = flats.getFirst().id;
      p.courier = "Blue Dart";
      p.description = "One sealed package";
      p.status = "WAITING";
      p.createdBy = watchman.id;
      db.save(p);
      Complaint c = new Complaint();
      c.societyId = a.id;
      c.flatId = flats.getFirst().id;
      c.title = "Water leakage near the lift lobby";
      c.description =
          "There is a slow drip near the first-floor lift. Please arrange an inspection.";
      c.category = "LEAKAGE";
      c.priority = "HIGH";
      c.location = "Wing A · first floor";
      c.status = "NEW";
      c.createdBy = resident.id;
      db.save(c);
      ComplaintHistory h = new ComplaintHistory();
      h.societyId = a.id;
      h.complaintId = c.id;
      h.actorId = resident.id;
      h.status = "NEW";
      h.comment = "Complaint raised";
      db.save(h);
      Notice notice = new Notice();
      notice.societyId = a.id;
      notice.title = "Let’s keep our community beautiful";
      notice.content =
          "Our monthly community clean-up is this Sunday at 9 am. Meet us in the central garden."
              + " Everyone is welcome!";
      notice.noticeType = "GENERAL";
      notice.audience = "ENTIRE_SOCIETY";
      notice.targetIds = "";
      notice.publishDate = Instant.now();
      notice.acknowledgementRequired = true;
      notice.createdBy = secretary.id;
      db.save(notice);
      Notice important = new Notice();
      important.societyId = a.id;
      important.title = "Water tank cleaning";
      important.content =
          "Water supply will pause from 10 am to noon tomorrow for scheduled tank cleaning. Please"
              + " store enough water for your household.";
      important.noticeType = "WATER_SHUTDOWN";
      important.audience = "ENTIRE_SOCIETY";
      important.targetIds = "";
      important.publishDate = Instant.now();
      important.createdBy = secretary.id;
      db.save(important);
      Meeting m = new Meeting();
      m.societyId = a.id;
      m.title = "October community catch-up";
      m.meetingType = "AGM";
      m.startsAt = Instant.now().plusSeconds(3 * 86400);
      m.venue = "Community hall, ground floor";
      m.participantIds = secretary.id + "," + resident.id;
      m.agendaJson =
          "[{\"id\":\""
              + UUID.randomUUID()
              + "\",\"sequenceNumber\":1,\"title\":\"Common area"
              + " improvements\",\"description\":\"Discuss lighting and garden"
              + " maintenance\",\"discussion\":\"\",\"decision\":\"\"}]";
      m.attendanceJson = "[]";
      m.status = "SCHEDULED";
      m.momStatus = "DRAFT";
      m.createdBy = secretary.id;
      db.save(m);
      ActionTask task = new ActionTask();
      task.societyId = a.id;
      task.title = "Inspect the lift lobby leak";
      task.description = "Arrange an inspection and share findings at the next meeting.";
      task.assignedTo = secretary.id;
      task.meetingId = m.id;
      task.priority = "HIGH";
      task.status = "OPEN";
      task.dueDate = LocalDate.now().plusDays(2);
      task.createdBy = secretary.id;
      db.save(task);
      for (Society s : List.of(a, b)) {
        EmergencyContact e = new EmergencyContact();
        e.societyId = s.id;
        e.name = "National emergency helpline";
        e.category = "Emergency";
        e.phone = "112";
        e.description = "For urgent police, fire or medical assistance";
        e.active = true;
        db.save(e);
      }
    }
  }

  private Account account(String name, String email, String mobile, String password) {
    Account a = new Account();
    a.name = name;
    a.email = email == null || email.isBlank() ? null : email.toLowerCase(Locale.ROOT);
    a.mobile = mobile == null || mobile.isBlank() ? null : mobile;
    a.passwordHash = passwords.encode(password);
    a.status = "ACTIVE";
    return db.save(a);
  }

  private Society society(String name, String code) {
    Society s = new Society();
    s.name = name;
    s.code = code;
    s.address = "Baner Road";
    s.city = "Pune";
    s.state = "Maharashtra";
    s.pinCode = "411045";
    s.timezone = "Asia/Kolkata";
    s.status = "ACTIVE";
    return db.save(s);
  }

  private void member(Society s, Account a, String roles) {
    Membership m = new Membership();
    m.societyId = s.id;
    m.userId = a.id;
    m.roles = roles;
    m.status = "ACTIVE";
    m.joinedAt = Instant.now();
    db.save(m);
  }

  private Flat flat(Society s, String number) {
    Flat f = new Flat();
    f.societyId = s.id;
    f.flatNumber = number;
    f.occupancyStatus = "OWNER_OCCUPIED";
    f.ownershipType = "INDIVIDUAL";
    f.carpetArea = new java.math.BigDecimal("850");
    f.active = true;
    return db.save(f);
  }

  private void profile(Society s, Account a, Flat f) {
    ResidentProfile p = new ResidentProfile();
    p.societyId = s.id;
    p.userId = a.id;
    p.name = a.name;
    p.email = a.email;
    p.mobile = a.mobile;
    p.active = true;
    db.save(p);
    ResidentFlatMapping m = new ResidentFlatMapping();
    m.societyId = s.id;
    m.residentId = p.id;
    m.flatId = f.id;
    m.relationshipType = "OWNER";
    m.startDate = LocalDate.now().minusYears(1);
    m.primaryFlat = true;
    db.save(m);
  }
}
