# Persistent sign-in — October 2026

## Root causes fixed

Previously the access JWT and society hint were stored only in `sessionStorage`. Closing the tab/PWA discarded them. The JWT expired after one hour with no refresh mechanism. On startup an unavailable `/auth/me` left the in-memory user empty, so the route guard could display login even though the stored JWT had not been cleared. Context activation also deleted saved hints before the server confirmed the new context.

## Current policy

- Access JWT: ten minutes, **memory only**. No JWT/password/refresh credential in localStorage, sessionStorage, source or service-worker caches.
- Persistent session: opaque 256-bit credential in a host-only `__Host-ms-session` cookie with **Secure; HttpOnly; SameSite=None; Partitioned; Path=/**. Browser-controlled expiry is at most 30 days. The server also enforces **30-day absolute / seven-day idle expiry**.
- PostgreSQL stores only token hashes, session/account IDs, revision and expiry metadata. Flyway V5 adds session and replay-history tables. Expired session histories are pruned on login.
- Refresh rotates the credential and returns a fresh access token. Concurrent refreshes are coalesced in the client. A 60-second server retry window returns the same successor for parallel tabs or a lost response; replay of a previously consumed token outside that window revokes the session family. Unknown guesses cannot revoke another person's session.
- Refresh and logout require an exact allowed Origin plus the custom `X-MS-Session` header. Credentialed CORS allows only configured origins, never `*`. Normal business APIs require a bearer access token, not just a cookie.
- Every protected request validates account status/revision and the server session. Password/login changes, recovery, owner-assisted resets and suspension invalidate existing access and refresh credentials. Logout revokes the current device session immediately when online.

## Startup, outages and society selection

`ms.session=active` in localStorage is only a non-secret hint to attempt cookie restoration. Society/managed-society IDs are non-authoritative preferences. No cached user, roles or permissions are trusted. `/auth/me`, memberships and active society permissions are fetched again before entering protected screens.

One approved society opens automatically; multiple memberships restore the saved approved selection or show the chooser. Super Admin's managed society is independently revalidated. Invalid/removed contexts clear the preference, not the account session. Context hints are retained during temporary connection failures.

Connection failures/timeouts/502–504 do not clear authentication. Startup failures show **Unable to connect to the Society server.** with a retry action and periodic reconnection. Existing open screens retain their session and show the connection banner. Genuine 401 responses clear invalid local authentication. Expired access tokens renew before requests; expired/revoked persistent sessions require sign-in.

Offline logout immediately removes local access and records only `ms.session=logout`. Startup must complete server logout before attempting restoration; it never restores a pending-logout cookie. Other open tabs receive the logout notification. Server invalidation cannot happen while the server is unreachable; it occurs on the next successful logout retry. A late refresh response cannot restore a locally ended session.

## Browser/deployment considerations

GitHub Pages and ngrok are different sites. Ordinary third-party cookies can be blocked, so the session cookie opts into partitioned storage (CHIPS). The persistent credential is not exposed as a JavaScript-storage fallback. Use a current browser that supports the cookie policy. Browsers/privacy policies that reject this cookie cannot provide persistent reopening; private browsing or clearing site data also ends local persistence. Same-site frontend/API hosting is the preferred future deployment for widest compatibility.

Changing the **ngrok hostname** changes the cookie host: users must sign in once on the new API origin. Ordinary frontend deployments on the same Pages origin with the same API hostname preserve sessions. The migration from old JWT-only sessions also requires one fresh sign-in. Backend downtime retains the local hint; it does not extend server-side expiry. A refresh response lost for longer than the replay grace window may require sign-in again for replay safety.

HttpOnly protects cookie values from script reads, but does not make XSS harmless: malicious script could still act in an open session. Preserve Angular's escaping, avoid unsafe HTML, keep dependencies updated, and never commit browser profiles/cookies. Test profiles are generated under ignored `.local/browser-profiles` and removed after verification.

## Verification

Backend tests cover cookie flags, ten-minute access expiry, rotation, duplicate retry, replay revocation, absolute/idle expiry, CSRF rejection, logout, password/login-detail changes, owner-assisted reset and suspension. Existing permissions, signup, approval and cross-society tests remain enabled.

Frontend tests cover in-memory-only tokens, refresh coalescing, outage preservation and retry, context restoration, real 401 handling, offline logout and late-response protection. Browser tests cover actual Chromium close/relaunch using a private temporary profile, owner managed-society restoration, resident/multi-society reopen, navigation/reload, simulated outage/401, and online/offline logout. The same read-only owner checks run against deployed Pages using the real public API; no live test users are created.

```powershell
.\scripts\run-browser-tests.ps1
# Deployed checks (owner credentials loaded privately from .env):
cd frontend
$env:HOSTED_URL='https://sarvesh1barve.github.io/MySociety/'
$env:EXPECTED_API_URL='<verified HTTPS API origin>'
$env:HOSTED_LOGIN_CHECK='1'
npx playwright test e2e/hosted.spec.ts e2e/session-persistence.spec.ts
```

References: [MDN partitioned cookies](https://developer.mozilla.org/en-US/docs/Web/Privacy/Guides/Third-party_cookies/Partitioned_cookies), [MDN third-party cookie restrictions](https://developer.mozilla.org/en-US/docs/Web/Privacy/Guides/Third-party_cookies), [RFC 9700 refresh-token rotation and revocation](https://www.rfc-editor.org/rfc/rfc9700.html).
