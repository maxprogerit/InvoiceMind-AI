-- Single sign-on: the identity provider's stable subject is bound to the user on first SSO login, so a later change
-- of e-mail address at the IdP cannot hand an account to somebody else.
ALTER TABLE users ADD COLUMN oidc_issuer  VARCHAR(300);
ALTER TABLE users ADD COLUMN oidc_subject VARCHAR(200);
CREATE UNIQUE INDEX ux_users_oidc ON users (oidc_issuer, oidc_subject) WHERE oidc_subject IS NOT NULL;
