-- Currency conversion for analytics.
-- Every rate is stored against the euro (the pivot of the ECB reference rates): 1 EUR = per_eur units of `currency`.
-- A cross rate is derived at query time, so one table serves every base currency.

ALTER TABLE organizations ADD COLUMN base_currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

CREATE TABLE exchange_rates (
  id               BIGSERIAL PRIMARY KEY,
  organization_id  UUID REFERENCES organizations(id) ON DELETE CASCADE,  -- NULL = shared ECB reference rate
  rate_date        DATE         NOT NULL,
  currency         VARCHAR(3)   NOT NULL,
  per_eur          NUMERIC(20,8) NOT NULL CHECK (per_eur > 0),
  source           VARCHAR(10)  NOT NULL CHECK (source IN ('ECB', 'MANUAL')),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_rates_shared ON exchange_rates (rate_date, currency) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX ux_rates_org    ON exchange_rates (organization_id, rate_date, currency) WHERE organization_id IS NOT NULL;
CREATE INDEX ix_rates_lookup ON exchange_rates (currency, rate_date DESC);

-- Which date range of the shared ECB rates has been downloaded (a single row).
CREATE TABLE fx_sync_state (
  id            SMALLINT PRIMARY KEY CHECK (id = 1),
  covered_from  DATE,
  covered_to    DATE,
  last_attempt  TIMESTAMPTZ,
  last_error    VARCHAR(300)
);
INSERT INTO fx_sync_state (id) VALUES (1);
