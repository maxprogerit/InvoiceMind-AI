-- Firms: an accounting firm sits above its client companies.
--   organization  = a client company; it stays the data tenant (every business row keeps its organization_id)
--   firm          = the group of client companies one team of accountants serves
-- A person reaches a client company either through user_org_access (explicit grant) or as FIRM_ADMIN of the owning firm.
-- users.organization_id stays as the person's home company, the one a login or a refresh lands in by default.

CREATE TABLE firms (
  id                     UUID PRIMARY KEY,
  name                   VARCHAR(200) NOT NULL,
  plan                   VARCHAR(32)  NOT NULL DEFAULT 'FREE',
  monthly_document_limit INT          CHECK (monthly_document_limit IS NULL OR monthly_document_limit >= 0),  -- NULL = unlimited
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Nullable on purpose: rows that predate firms are backfilled below, and code that inserts a bare organisation keeps working.
ALTER TABLE organizations ADD COLUMN firm_id UUID REFERENCES firms(id);
-- A deactivated client cannot be switched into; its data is kept.
ALTER TABLE organizations ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX ix_organizations_firm ON organizations (firm_id);

CREATE TABLE firm_members (
  firm_id    UUID        NOT NULL REFERENCES firms(id) ON DELETE CASCADE,
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  firm_role  VARCHAR(20) NOT NULL CHECK (firm_role IN ('FIRM_ADMIN', 'ACCOUNTANT')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (firm_id, user_id)
);
CREATE INDEX ix_firm_members_user ON firm_members (user_id);

CREATE TABLE user_org_access (
  user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  organization_id  UUID        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  role             VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'APPROVER', 'ADMIN')),  -- the role the person has in THAT company
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, organization_id)
);
CREATE INDEX ix_user_org_access_org ON user_org_access (organization_id);

-- Which company a session is working in survives a token refresh (see AuthService.refresh).
ALTER TABLE refresh_tokens ADD COLUMN active_organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL;

-- Backfill. Every existing company becomes a firm of one, with the same name. The firm reuses the organisation's id:
-- it is unique, needs no mapping table, and makes the migration deterministic.
INSERT INTO firms (id, name, created_at) SELECT id, name, created_at FROM organizations;
UPDATE organizations SET firm_id = id;

-- Everybody keeps exactly the access they have today: their home company, with their current role.
INSERT INTO user_org_access (user_id, organization_id, role) SELECT id, organization_id, role FROM users;

-- The admins of a firm of one are its FIRM_ADMINs.
INSERT INTO firm_members (firm_id, user_id, firm_role)
SELECT organization_id, id, 'FIRM_ADMIN' FROM users WHERE role = 'ADMIN';
