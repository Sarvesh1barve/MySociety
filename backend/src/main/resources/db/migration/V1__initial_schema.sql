CREATE TABLE accounts (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 name text,
 email text,
 mobile text,
 password_hash text,
 status text,
 super_admin boolean NOT NULL DEFAULT false
);
CREATE TABLE societies (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 name text,
 code text,
 registration_number text,
 address text,
 city text,
 state text,
 pin_code text,
 timezone text,
 email text,
 phone text,
 status text,
 logo_url text,
 settings_json text
);
CREATE TABLE memberships (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 user_id uuid,
 status text,
 joined_at timestamptz,
 left_at timestamptz,
 primary_membership boolean NOT NULL DEFAULT false,
 roles text
);
CREATE UNIQUE INDEX memberships_tenant_id ON memberships(society_id,id);
CREATE INDEX memberships_tenant_created ON memberships(society_id,created_at);
CREATE TABLE role_definitions (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 code text,
 permissions text
);
CREATE TABLE application_settings (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 application_name text,
 short_name text,
 logo_url text,
 favicon_url text,
 footer_text text,
 support_email text,
 support_phone text,
 updated_at timestamptz,
 updated_by uuid
);
CREATE TABLE buildings (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 name text,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX buildings_tenant_id ON buildings(society_id,id);
CREATE INDEX buildings_tenant_created ON buildings(society_id,created_at);
CREATE TABLE wings (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 building_id uuid,
 name text,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX wings_tenant_id ON wings(society_id,id);
CREATE INDEX wings_tenant_created ON wings(society_id,created_at);
CREATE TABLE floors (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 building_id uuid,
 wing_id uuid,
 name text,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX floors_tenant_id ON floors(society_id,id);
CREATE INDEX floors_tenant_created ON floors(society_id,created_at);
CREATE TABLE flats (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 building_id uuid,
 wing_id uuid,
 floor_id uuid,
 flat_number text,
 carpet_area numeric(18,2),
 occupancy_status text,
 ownership_type text,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX flats_tenant_id ON flats(society_id,id);
CREATE INDEX flats_tenant_created ON flats(society_id,created_at);
CREATE TABLE resident_profiles (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 user_id uuid,
 name text,
 mobile text,
 email text,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX resident_profiles_tenant_id ON resident_profiles(society_id,id);
CREATE INDEX resident_profiles_tenant_created ON resident_profiles(society_id,created_at);
CREATE TABLE resident_flat_mappings (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 resident_id uuid,
 flat_id uuid,
 relationship_type text,
 start_date date,
 end_date date,
 primary_flat boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX resident_flat_mappings_tenant_id ON resident_flat_mappings(society_id,id);
CREATE INDEX resident_flat_mappings_tenant_created ON resident_flat_mappings(society_id,created_at);
CREATE TABLE visitors (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 visitor_name text,
 mobile_number text,
 visitor_type text,
 flat_id uuid,
 purpose text,
 vehicle_number text,
 approval_status text,
 status text,
 expected_from timestamptz,
 expected_until timestamptz,
 entry_time timestamptz,
 exit_time timestamptz,
 created_by uuid,
 approved_by uuid
);
CREATE UNIQUE INDEX visitors_tenant_id ON visitors(society_id,id);
CREATE INDEX visitors_tenant_created ON visitors(society_id,created_at);
CREATE TABLE parcels (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 flat_id uuid,
 courier text,
 description text,
 status text,
 collected_at timestamptz,
 collected_by uuid,
 created_by uuid
);
CREATE UNIQUE INDEX parcels_tenant_id ON parcels(society_id,id);
CREATE INDEX parcels_tenant_created ON parcels(society_id,created_at);
CREATE TABLE complaints (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 title text,
 description text,
 category text,
 priority text,
 location text,
 flat_id uuid,
 attachment_ids text,
 created_by uuid,
 assigned_to uuid,
 status text
);
CREATE UNIQUE INDEX complaints_tenant_id ON complaints(society_id,id);
CREATE INDEX complaints_tenant_created ON complaints(society_id,created_at);
CREATE TABLE complaint_history (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 complaint_id uuid,
 actor_id uuid,
 status text,
 comment text
);
CREATE UNIQUE INDEX complaint_history_tenant_id ON complaint_history(society_id,id);
CREATE INDEX complaint_history_tenant_created ON complaint_history(society_id,created_at);
CREATE TABLE notices (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 title text,
 content text,
 notice_type text,
 audience text,
 target_ids text,
 publish_date timestamptz,
 expiry_date timestamptz,
 attachment_id uuid,
 acknowledgement_required boolean NOT NULL DEFAULT false,
 created_by uuid
);
CREATE UNIQUE INDEX notices_tenant_id ON notices(society_id,id);
CREATE INDEX notices_tenant_created ON notices(society_id,created_at);
CREATE TABLE acknowledgements (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 notice_id uuid,
 user_id uuid
);
CREATE UNIQUE INDEX acknowledgements_tenant_id ON acknowledgements(society_id,id);
CREATE INDEX acknowledgements_tenant_created ON acknowledgements(society_id,created_at);
CREATE TABLE meetings (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 title text,
 meeting_type text,
 starts_at timestamptz,
 venue text,
 participant_ids text,
 agenda_json text,
 attendance_json text,
 resolutions text,
 attachment_ids text,
 status text,
 mom_status text,
 approved_by uuid,
 approved_at timestamptz,
 created_by uuid
);
CREATE UNIQUE INDEX meetings_tenant_id ON meetings(society_id,id);
CREATE INDEX meetings_tenant_created ON meetings(society_id,created_at);
CREATE TABLE action_tasks (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 title text,
 description text,
 assigned_to uuid,
 meeting_id uuid,
 agenda_item_id text,
 due_date date,
 priority text,
 status text,
 completed_at timestamptz,
 created_by uuid
);
CREATE UNIQUE INDEX action_tasks_tenant_id ON action_tasks(society_id,id);
CREATE INDEX action_tasks_tenant_created ON action_tasks(society_id,created_at);
CREATE TABLE documents (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 file_name text,
 storage_key text,
 content_type text,
 size bigint NOT NULL DEFAULT 0,
 visibility text,
 uploaded_by uuid,
 revision integer NOT NULL DEFAULT 1,
 previous_version_id uuid
);
CREATE UNIQUE INDEX documents_tenant_id ON documents(society_id,id);
CREATE INDEX documents_tenant_created ON documents(society_id,created_at);
CREATE TABLE notifications (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 user_id uuid,
 type text,
 title text,
 message text,
 related_entity_type text,
 related_entity_id uuid,
 read_at timestamptz
);
CREATE UNIQUE INDEX notifications_tenant_id ON notifications(society_id,id);
CREATE INDEX notifications_tenant_created ON notifications(society_id,created_at);
CREATE TABLE emergency_contacts (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 name text,
 category text,
 phone text,
 description text,
 active boolean NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX emergency_contacts_tenant_id ON emergency_contacts(society_id,id);
CREATE INDEX emergency_contacts_tenant_created ON emergency_contacts(society_id,created_at);
CREATE TABLE audit_logs (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid,
 actor_user_id uuid,
 action text,
 entity_type text,
 entity_id uuid,
 old_value_json text,
 new_value_json text,
 correlation_id text
);

CREATE UNIQUE INDEX accounts_email ON accounts(lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX accounts_mobile ON accounts(mobile) WHERE mobile IS NOT NULL;
ALTER TABLE accounts ALTER COLUMN name SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE accounts ADD CONSTRAINT account_login CHECK (email IS NOT NULL OR mobile IS NOT NULL);
CREATE UNIQUE INDEX society_code ON societies(code);
CREATE UNIQUE INDEX role_code ON role_definitions(code);
CREATE UNIQUE INDEX membership_user_society ON memberships(user_id,society_id);
ALTER TABLE memberships ADD FOREIGN KEY(user_id) REFERENCES accounts(id);
ALTER TABLE resident_profiles ADD FOREIGN KEY(user_id) REFERENCES accounts(id);
CREATE UNIQUE INDEX resident_user_society ON resident_profiles(society_id,user_id);
CREATE UNIQUE INDEX flat_number_society ON flats(society_id,flat_number);
CREATE UNIQUE INDEX notice_user_ack ON acknowledgements(notice_id,user_id);
ALTER TABLE acknowledgements ADD FOREIGN KEY(society_id,notice_id) REFERENCES notices(society_id,id);
ALTER TABLE resident_flat_mappings ADD FOREIGN KEY(society_id,resident_id) REFERENCES resident_profiles(society_id,id);
ALTER TABLE resident_flat_mappings ADD FOREIGN KEY(society_id,flat_id) REFERENCES flats(society_id,id);
ALTER TABLE wings ADD FOREIGN KEY(society_id,building_id) REFERENCES buildings(society_id,id);
ALTER TABLE floors ADD FOREIGN KEY(society_id,building_id) REFERENCES buildings(society_id,id);
ALTER TABLE floors ADD FOREIGN KEY(society_id,wing_id) REFERENCES wings(society_id,id);
ALTER TABLE flats ADD FOREIGN KEY(society_id,building_id) REFERENCES buildings(society_id,id);
ALTER TABLE flats ADD FOREIGN KEY(society_id,wing_id) REFERENCES wings(society_id,id);
ALTER TABLE flats ADD FOREIGN KEY(society_id,floor_id) REFERENCES floors(society_id,id);
ALTER TABLE visitors ADD FOREIGN KEY(society_id,flat_id) REFERENCES flats(society_id,id);
ALTER TABLE parcels ADD FOREIGN KEY(society_id,flat_id) REFERENCES flats(society_id,id);
ALTER TABLE complaints ADD FOREIGN KEY(society_id,flat_id) REFERENCES flats(society_id,id);
ALTER TABLE complaint_history ADD FOREIGN KEY(society_id,complaint_id) REFERENCES complaints(society_id,id);
ALTER TABLE action_tasks ADD FOREIGN KEY(society_id,meeting_id) REFERENCES meetings(society_id,id);
ALTER TABLE documents ADD FOREIGN KEY(society_id,previous_version_id) REFERENCES documents(society_id,id);
CREATE INDEX visitor_status ON visitors(society_id,status,flat_id);
CREATE INDEX parcel_status ON parcels(society_id,status,flat_id);
CREATE INDEX complaint_status ON complaints(society_id,status,created_by);
CREATE INDEX meeting_date ON meetings(society_id,starts_at);
CREATE INDEX notification_user ON notifications(society_id,user_id,read_at);
CREATE INDEX resident_mapping ON resident_flat_mappings(society_id,resident_id,flat_id);
CREATE INDEX audit_society ON audit_logs(society_id,created_at);
