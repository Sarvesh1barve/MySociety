CREATE TABLE membership_requests (
 id uuid PRIMARY KEY,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(),
 society_id uuid NOT NULL REFERENCES societies(id),
 user_id uuid NOT NULL REFERENCES accounts(id),
 flat_hint text NOT NULL,
 status text NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 review_note text,
 reviewed_by uuid REFERENCES accounts(id),
 reviewed_at timestamptz,
 UNIQUE(society_id,user_id)
);
CREATE INDEX membership_request_queue ON membership_requests(society_id,status,created_at);

-- The unique index deliberately aborts this migration if legacy phone aliases conflict.
UPDATE accounts SET mobile = CASE
 WHEN length(regexp_replace(mobile,'[ +()-]','','g'))=10
 THEN '+91' || regexp_replace(mobile,'[ +()-]','','g')
 ELSE '+' || regexp_replace(mobile,'[ +()-]','','g') END
WHERE mobile IS NOT NULL;
