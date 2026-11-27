-- Roles: USER becomes ACCOUNTANT (uploads, reviews and corrects; sees the whole organisation; cannot approve).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
UPDATE users SET role = 'ACCOUNTANT' WHERE role = 'USER';
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ACCOUNTANT', 'APPROVER', 'ADMIN'));
ALTER TABLE user_org_access DROP CONSTRAINT IF EXISTS user_org_access_role_check;
UPDATE user_org_access SET role = 'ACCOUNTANT' WHERE role = 'USER';
ALTER TABLE user_org_access ADD CONSTRAINT user_org_access_role_check CHECK (role IN ('ACCOUNTANT', 'APPROVER', 'ADMIN'));

-- Approval rules per currency. Totals are compared by absolute value (credit notes are stored negative).
--   below auto_approve_below            -> approved automatically once it passes every check
--   up to two_approvers_above           -> one approver
--   above two_approvers_above           -> two different approvers
-- A currency without a row needs one approver for every amount.
CREATE TABLE approval_policies (
  organization_id      UUID          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  currency             VARCHAR(3)    NOT NULL,
  auto_approve_below   NUMERIC(14,2) CHECK (auto_approve_below IS NULL OR auto_approve_below >= 0),
  two_approvers_above  NUMERIC(14,2) CHECK (two_approvers_above IS NULL OR two_approvers_above >= 0),
  updated_by           UUID          REFERENCES users(id),
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, currency),
  CHECK (auto_approve_below IS NULL OR two_approvers_above IS NULL OR auto_approve_below <= two_approvers_above)
);

-- Individual approvals; an invoice is APPROVED when it has as many as its rule requires. Nobody twice.
CREATE TABLE invoice_approvals (
  id          BIGSERIAL     PRIMARY KEY,
  invoice_id  UUID          NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  user_id     UUID          NOT NULL REFERENCES users(id),
  comment     VARCHAR(2000),
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (invoice_id, user_id)
);
ALTER TABLE invoices ADD COLUMN auto_approved BOOLEAN NOT NULL DEFAULT false;

-- One reminder digest per organisation and day, also with several backend instances.
CREATE TABLE reminder_runs (
  organization_id  UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  run_date         DATE         NOT NULL,
  due_soon         INT          NOT NULL,
  overdue          INT          NOT NULL,
  recipients       INT          NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, run_date)
);
