-- P068 V1.2 P1 探针：在共享免费环境的 public schema 下建隔离探针表
-- 仅用于验证 PG + RLS + REST；探针结束后整表删除（DROP TABLE public.p068_probe_sessions）。
-- 不建在 public 之外：CloudBase REST 只暴露 public。

create table public.p068_probe_sessions (
  id varchar(64) primary key,
  owner_uid varchar(64) not null default auth.uid(),
  mode text not null check (mode in ('TIME', 'REPS')),
  total_seconds integer not null default 0,
  total_reps integer not null default 0,
  revision integer not null default 1,
  client_updated_at bigint not null,
  server_updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

grant select, insert, update, delete on public.p068_probe_sessions to authenticated;

alter table public.p068_probe_sessions enable row level security;

create policy p068_probe_select_own on public.p068_probe_sessions
  for select to authenticated using (owner_uid = auth.uid());

create policy p068_probe_insert_own on public.p068_probe_sessions
  for insert to authenticated with check (owner_uid = auth.uid());

create policy p068_probe_update_own on public.p068_probe_sessions
  for update to authenticated using (owner_uid = auth.uid()) with check (owner_uid = auth.uid());

create policy p068_probe_delete_own on public.p068_probe_sessions
  for delete to authenticated using (owner_uid = auth.uid());
