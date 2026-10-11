-- One-time/repeatable cleanup of the exact inspected development installation.
-- Take scripts/backup.ps1 first. Never broaden this UUID allowlist to all non-admin users.
\set ON_ERROR_STOP on
BEGIN;
LOCK TABLE accounts IN SHARE ROW EXCLUSIVE MODE;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM accounts WHERE id='c49e5c6c-c6d6-473c-8671-5f546f2887ac' AND super_admin AND status='ACTIVE')
 THEN RAISE EXCEPTION 'Expected real platform owner not found; cleanup aborted'; END IF;
END $$;
CREATE TEMP TABLE cleanup_users(id uuid PRIMARY KEY) ON COMMIT DROP;
INSERT INTO cleanup_users
SELECT id FROM accounts WHERE NOT super_admin AND (id,email) IN (
 ('dea57e9e-88d4-4add-b57f-b08a28e800ec'::uuid,'secretary@demo.local'),
 ('c180680c-7475-4e65-a228-0a53b25c4c3b'::uuid,'watchman@demo.local'),
 ('a02e3a15-8586-46d6-b26e-dbc02e747fe2'::uuid,'resident@demo.local'),
 ('ba98197d-3aaf-4580-ad78-c1b023d85baf'::uuid,'resident2@demo.local'),
 ('1a5c9061-98fe-4b0c-8f59-dfe1324d1322'::uuid,'browser-recovery-1791616063167@test.local'),
 ('79dd89c4-deec-4042-9920-165a45143bc5'::uuid,'browser-recovery-1791616190345@test.local'),
 ('89bb1f8e-f854-4aa1-8475-723f811029a3'::uuid,'browser-recovery-1791616273019@test.local'),
 ('cdd4963d-161a-4f3d-9513-4766d7d0d323'::uuid,'browser-renamed-1791616335187@test.local'),
 ('a77cd054-d4af-4774-8465-d20a93616b36'::uuid,'browser-renamed-1791616447634@test.local')
);
-- This profile was edited by the owner: retain the person's details/home, remove only demo login.
UPDATE resident_profiles SET user_id=NULL, version=version+1
 WHERE id='eb749dbe-4acd-4b00-925a-b95ac6448e0c' AND user_id IN (SELECT id FROM cleanup_users);
DELETE FROM resident_flat_mappings WHERE resident_id IN (SELECT id FROM resident_profiles WHERE user_id IN (SELECT id FROM cleanup_users));
DELETE FROM resident_profiles WHERE user_id IN (SELECT id FROM cleanup_users);
DELETE FROM complaint_history WHERE actor_id IN (SELECT id FROM cleanup_users) OR complaint_id IN (SELECT id FROM complaints WHERE created_by IN (SELECT id FROM cleanup_users));
DELETE FROM action_tasks WHERE created_by IN (SELECT id FROM cleanup_users);
DELETE FROM acknowledgements WHERE user_id IN (SELECT id FROM cleanup_users) OR notice_id IN (SELECT id FROM notices WHERE created_by IN (SELECT id FROM cleanup_users));
DELETE FROM notices WHERE created_by IN (SELECT id FROM cleanup_users);
DELETE FROM meetings WHERE created_by IN (SELECT id FROM cleanup_users);
DELETE FROM complaints WHERE created_by IN (SELECT id FROM cleanup_users);
DELETE FROM visitors WHERE created_by IN (SELECT id FROM cleanup_users);
DELETE FROM parcels WHERE created_by IN (SELECT id FROM cleanup_users);
DELETE FROM notifications WHERE user_id IN (SELECT id FROM cleanup_users);
DELETE FROM recovery_codes WHERE user_id IN (SELECT id FROM cleanup_users);
DELETE FROM memberships WHERE user_id IN (SELECT id FROM cleanup_users);
DELETE FROM audit_logs WHERE actor_user_id IN (SELECT id FROM cleanup_users)
 OR (entity_type='Account' AND entity_id IN (SELECT id FROM cleanup_users));
-- Any unexpected remaining foreign key dependency aborts and rolls back the whole cleanup.
DELETE FROM accounts WHERE id IN (SELECT id FROM cleanup_users) RETURNING id,email;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM accounts WHERE id='c49e5c6c-c6d6-473c-8671-5f546f2887ac' AND super_admin)
 THEN RAISE EXCEPTION 'Owner preservation check failed'; END IF;
END $$;
COMMIT;
SELECT name,email,status,super_admin FROM accounts ORDER BY super_admin DESC,created_at;
