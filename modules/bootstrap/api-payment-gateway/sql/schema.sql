-- Schema reference for hiring task
-- partner master
create table if not exists partner (
  id bigint auto_increment primary key,
  code varchar(64) not null unique,
  name varchar(255) not null,
  active boolean not null default true
);

-- partner fee policy
create table if not exists partner_fee_policy (
  id bigint auto_increment primary key,
  partner_id bigint not null,
  effective_from timestamp not null,
  percentage decimal(10,6) not null,
  fixed_fee decimal(15,0) null,
  index idx_fee_partner_from (partner_id, effective_from desc)
);

-- payment history
create table if not exists payment (
  id bigint auto_increment primary key,
  partner_id bigint not null,
  amount decimal(15,0) not null,
  applied_fee_rate decimal(10,6) not null,
  fee_amount decimal(15,0) not null,
  net_amount decimal(15,0) not null,
  card_bin varchar(8) null,
  card_last4 varchar(4) null,
  approval_code varchar(32) not null,
  approved_at timestamp not null,
  status varchar(20) not null,
  created_at timestamp not null,
  updated_at timestamp not null,
  index idx_payment_created (created_at desc, id desc),
  index idx_payment_partner_created (partner_id, created_at desc)
);

-- seed
-- Use idempotent seed to avoid duplicate key errors when the app restarts
INSERT IGNORE INTO partner (code, name, active) VALUES ('TEST', 'Test Partner', true);

-- Ensure a default fee policy exists for the TEST partner so payments can be created immediately
INSERT INTO partner_fee_policy (partner_id, effective_from, percentage, fixed_fee)
SELECT p.id, '2020-01-01 00:00:00', 0.025000, 0
FROM partner p
WHERE p.code = 'TEST'
  AND NOT EXISTS (
    SELECT 1 FROM partner_fee_policy f WHERE f.partner_id = p.id
  );
