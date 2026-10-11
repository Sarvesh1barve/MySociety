# Sarvesh Barve — complete Super Admin guide

## 1. Open the application and sign in

Website: **https://sarvesh1barve.github.io/MySociety/**

Your initial login email is **owner@mysociety.local**. There is no separate username: an account's email or mobile number is its login identifier. This is the initial owner login address, not an email inbox. Change it in Your profile.

Your exact initial password is stored privately on this workstation in `D:\MySociety\.env`, under `INITIAL_SUPER_ADMIN_PASSWORD`. A private handover file with your password and recovery codes is provided at `D:\MySociety\.local\SUPER_ADMIN_ACCESS.md`. Neither file is committed to GitHub. Do not share them with residents or society administrators.

The website is static hosting; sign-in requires this laptop's PostgreSQL, Spring Boot API and ngrok tunnel to remain running. Laptop sleep, shutdown, network loss or a stopped tunnel makes business operations unavailable.

## 2. Secure your owner account first

1. Sign in as the owner. Open **Your profile** using your avatar in the top-right corner (or `#/profile`).
2. Under your profile details, change **Your name** and Save.
3. Under **Change login email or mobile**, replace the initial email with your preferred email and optionally add your mobile number. At least one must remain. Confirm your **current password** and choose **Update login details**.
4. You are signed out everywhere. Sign in using your new email/mobile and existing password. Your global identity, Super Admin role and society relationships are preserved.
5. Under **Change password**, enter your current password and your new password twice. Use a unique password of at least 12 characters, at most 72 UTF-8 bytes. Save it in your password manager.
6. You are signed out everywhere again. Sign in with the new password.
7. Under **Password recovery codes**, enter your current password and select **Generate new recovery codes**. Download the five private codes and keep a separate offline copy. Generating codes replaces every earlier code, including the initial handover set.
8. Store your new login/password privately. Update or securely remove obsolete initial credential copies after checking your new login works.

**Important:** changing `INITIAL_SUPER_ADMIN_*` in `.env` does not rename or reset an existing owner. Those values only bootstrap an owner when none exists. Use the profile screens for later changes. No regular user or society administrator can grant themselves Super Admin.

## 3. Forgotten password — all user types

The chosen method is **one-time recovery codes**, not email, OTP or SMS. No SMTP account or paid service is required.

Before forgetting a password, every user should open **Your profile → Password recovery codes**, confirm their current password, generate codes and save them privately. The dashboard includes a reminder.

To recover:

1. On the sign-in screen, choose **Forgot password?**.
2. Enter the account's current login email or mobile number.
3. Enter one unused recovery code from that account.
4. Enter and confirm the new password.
5. Select **Reset password**, then sign in with the new password.
6. Cross out the used code. It cannot be used again. Existing sessions are invalidated. Remaining unused codes still work; generate a fresh set from your profile if any old code may have been exposed.

The same procedure works for Super Admin, committee members, managers, watchmen, owners, tenants, family members and auditors. Recovery does not reactivate suspended accounts or grant additional permissions. Login-detail changes do not invalidate unused codes; use the new login with those codes.

### If a user has no recovery codes

Only you, the platform Super Admin, can assist:

1. Independently verify the person's identity; do not trust an unsolicited password-reset request.
2. Open **Platform users** and find the exact user.
3. Choose **Reset password**. Enter **your Super Admin password** and a strong temporary password for that user.
4. Share the temporary password privately, never in a society group. Ask the user to sign in, change it immediately and save new recovery codes.

This revokes that user's previous sessions and all recovery codes. It does not reactivate a suspended account, change memberships, or automatically force a temporary-password change.

**This assisted reset cannot target any Super Admin.** If you lose both your owner password and every unused owner recovery code, there is intentionally no public or society-admin bypass. Recovery would require a controlled local database-administrator intervention. Do not wait until lockout to save your codes.

## 4. Create your real society

Use the default profile and keep tests in isolated databases. The known demo users have been cleaned from this installation; Jay Bharti and the owner-edited resident profile were preserved. Verify your society and flat details before inviting residents.

1. Open **Societies → Add society**.
2. Enter name, unique code, registration/address/contact details and timezone (normally `Asia/Kolkata`). Choose Active or Trial and an optional colour theme.
3. Save. No deployment or new database is required for another society.
4. Choose **Manage** on the society row. This gives you an explicit active society context.
5. Open **Flats & buildings**. Add building/wing/floor records as needed, then flats. Optional hierarchy levels can be omitted.
6. Open **Platform users → Add user** to create an individual's login with a strong temporary password. Then **Add membership** to the correct society and select one or more society roles. Alternatively, use the society's **Users & memberships** flow.
7. Give the first trusted society administrator the Secretary, Chairman or Society Manager role as appropriate. Do not share your Super Admin account.
8. Add residents and link each resident profile to its login, then create resident–flat mappings with the correct OWNER/TENANT/FAMILY_MEMBER relationship and dates.
9. Create an individual Watchman account/membership. Confirm an owner can approve a gate visitor and a watchman can check that visitor in and out.
10. Add emergency contacts, initial notices and society documents. Review role permissions before onboarding everyone.

## 5. Set different themes for different societies

Available palettes: **Forest, Ocean, Indigo, Rose, Amber and Slate**. These are light, preset palettes; warning/error colours retain their safety meaning.

- **Super Admin:** open **Societies → Edit → Colour theme → Save**, or Manage that society and use its Society settings.
- **Society administrator:** open **Society settings → Society colour theme**, choose a colour card, then **Apply society theme**. This needs the `SOCIETY_UPDATE` permission.
- The theme is stored on that society, not on the user/browser. Switching societies loads the destination society's theme. Other members see updates when they refresh or reopen/switch to the society.
- Ordinary residents/watchmen cannot change a society theme. A user who administers Society A does not thereby administer Society B.
- Platform/login pages without an active society use the default Forest palette. Theme selection does not alter platform/society names or the fixed Sarvesh Barve rights statement.

## 6. Change platform branding and roles

**Application branding** controls the platform name, short name, logo/favicon URLs, additional footer text and support contact details. Society names remain independent. The fixed **All rights reserved by Sarvesh Barve** line remains visible.

**Roles & permissions** controls platform-wide society-role definitions. Membership assignments remain society-specific. Changing a definition affects every society using that role; review carefully. The backend enforces permission changes on subsequent requests. Users can refresh or switch society to refresh navigation.

## 7. Start the services after restarting your laptop

Open PowerShell in `D:\MySociety`.

### PostgreSQL

This workstation uses a project-specific loopback-only PostgreSQL instance on **5433**, preserving the pre-existing 5432 service.

```powershell
cd D:\MySociety
& .\.tools\postgres\pgsql\bin\pg_ctl.exe status -D D:\MySociety\.local\postgres-data
# Only if this project instance is stopped:
& .\.tools\postgres\pgsql\bin\pg_ctl.exe start -D D:\MySociety\.local\postgres-data -l D:\MySociety\.local\postgres.log
```

### Backend

```powershell
cd D:\MySociety
$env:JAVA_HOME='C:\Users\LENOVO\AppData\Local\Programs\Microsoft\jdk-21.0.12.8-hotspot'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

Do not use `-Dev` for real usage; it enables API documentation only. The script reads `.env`, checks port availability, records the chosen port and updates local frontend runtime configuration. The current backend uses **8081** because an unrelated service already occupied 8080. Do not start a second copy when one is already running; do not stop an unrelated service.

In a second PowerShell window, verify the actual chosen port:

```powershell
Invoke-RestMethod http://localhost:8081/api/v1/system/health
```

### HTTPS tunnel

```powershell
cd D:\MySociety
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-ngrok.ps1
# Equivalent for the current API port:
# ngrok http 8081 --inspect=false
```

Keep the tunnel running. If its HTTPS URL changes, update the GitHub repository variable **API_BASE_URL** to the new origin without `/api/v1`, then rerun **Deploy GitHub Pages** under Actions. Backend CORS must include `https://sarvesh1barve.github.io`. Database credentials and owner passwords never go into GitHub frontend variables.

### Frontend

Residents use the hosted website; no frontend development server is needed for that. For local development:

```powershell
cd D:\MySociety\frontend
npm ci
npm start
```

Open `http://localhost:4200`.

## 8. Updates, backups and availability

- Backend changes require a new Maven package and restarting only the My Society API. Flyway applies versioned migrations; do not manually delete migration history.
- Frontend changes on `main` trigger the GitHub Pages workflow. After a deployment, close/reopen the installed PWA or refresh to allow the service worker to use the latest assets.
- Run `scripts/backup.ps1` for database plus uploads. Test restores into a separate database using `scripts/restore.ps1`; see BACKUP.md. Keep a protected copy off this laptop.
- Never upload `.env`, `.local`, recovery-code files, personal uploads or database dumps to GitHub.
- Use **Diagnostics** to confirm backend/database health and configured API address.
- The free laptop/tunnel setup is not always-on production hosting. Account recovery also requires the backend/database/tunnel to be available.

## Resident self-signup and approval

1. Share the website's **Create Account** link (`/#/signup`) and your society code with residents.
2. Residents enter full name, mobile, email and a password twice. Indian 10-digit mobiles are stored with +91; other numbers need a country code.
3. They sign in at `/#/login`, enter your society code and their building/flat, then choose **Request access**.
4. As Super Admin, open **Societies → Manage society → Join requests**. A society administrator can use **Join requests** directly.
5. Verify identity/occupancy independently, select the correct flat and owner/tenant/family relationship, then approve. Decline with an explanation when appropriate.
6. The resident selects **Refresh access**. One available membership opens automatically; multiple memberships show society selection. No membership means the clear onboarding screen, never a blank dashboard.
7. Committee/watchman roles are assigned separately through **Users & memberships → Access**, only by authorised administrators.

### Accounts versus resident details

**Platform users** are sign-in accounts. **Users & memberships** shows society roles and status. **Residents** stores contact information and the linked login; **Flat links** stores occupancy. Editing resident contact information does not rename the sign-in account. Use Platform users (or the user's own profile) to edit login identity.

An account created by Super Admin is initially shown as **No Society Assigned**. Use **Add membership** and then link its resident profile to a flat for home-specific functions. **Users & memberships → Add user** creates the account and society membership together. Approval of a self-signup creates membership, resident profile and flat mapping together.

The owner-edited resident profile and flat link were retained without a login when the demo account was deleted. Verify whether this is a real resident before linking a new approved account via **Residents → Edit → Linked login**. The two existing society configurations were preserved rather than deleting owner-edited setup.
