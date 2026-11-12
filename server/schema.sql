-- CarLink 프로토타입 중계 테이블 (Supabase / PostgreSQL)
-- 메시지 본문은 저장하지 않는다. 카탈로그의 문장 ID 와 방향 정보만 오간다.

create table if not exists public.messages (
  id            bigint generated always as identity primary key,
  created_at    timestamptz not null default now(),
  segment       text not null,                       -- 도로 구간 ID
  from_vehicle  text not null,                       -- 발신 차량 (프로토타입: CAR-A 등 임의 ID)
  from_order    integer not null,                    -- 구간 내 순서 (클수록 앞쪽)
  target        text not null check (target in ('FRONT','BACK','AHEAD_ALL','BEHIND_ALL')),
  sentence_id   text not null check (sentence_id ~ '^[A-Z][0-9]{2}$'),
  tier          text not null check (tier in ('COURTESY','EMERGENCY')),
  trust         text not null default 'NONE'
                check (trust in ('NONE','VEHICLE_SENSED','MULTI_REPORT','DRIVER_REPORT'))
);

create index if not exists messages_segment_id_idx on public.messages (segment, id);

-- 프로토타입은 로그인 없이 공개 키(anon)로 읽고 쓴다.
-- 저장되는 값이 문장 ID 뿐이라 개인정보는 없지만, 실제 서비스에서는 차량 계정 인증으로 바꿔야 한다.
alter table public.messages enable row level security;

drop policy if exists "prototype read" on public.messages;
create policy "prototype read" on public.messages
  for select to anon using (true);

drop policy if exists "prototype insert" on public.messages;
create policy "prototype insert" on public.messages
  for insert to anon with check (true);

-- 24시간이 지난 메시지 삭제 (pg_cron 확장을 켠 뒤 한 번 실행)
-- select cron.schedule('carlink-purge', '0 * * * *',
--   $$delete from public.messages where created_at < now() - interval '24 hours'$$);
