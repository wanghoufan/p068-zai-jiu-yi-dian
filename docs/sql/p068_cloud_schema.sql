-- P068 云同步正式表结构（CloudBase PostgreSQL / public schema）
-- 用途：供人工在 CloudBase 控制台 SQL 编辑器逐条执行。
-- 约定：
--   1. 只使用标准 PostgreSQL 语法（含 RLS 与 auth.uid()，CloudBase 已提供）；不写 Supabase 专有函数与扩展。
--   2. 同步单元＝project 与 session 两种；session 的每组明细以 JSONB 内嵌 payload，不单独建 rep_sets 表。
--   3. 归属列 owner_uid 默认取 auth.uid()；所有策略一律限定 owner_uid = auth.uid()。
--   4. 客户端冲突判定：PATCH ?id=eq.X&revision=eq.baseRevision 影响 0 行＝冲突；INSERT 409＝冲突。
--   5. 每条语句可独立执行；重复执行不会报错（建表/建索引用 IF NOT EXISTS，策略与触发器先 DROP IF EXISTS）。
--   6. 不给 anon 任何权限：匿名密钥只能读不到任何行，也无法写入。

-- ─────────────────────────────── 建表 ───────────────────────────────

-- 项目表：一行＝一个 project（含标题、模式等基础信息，明细放 payload）
create table if not exists public.p068_projects (
  id varchar(64) primary key,                          -- 客户端生成的全局唯一 ID
  owner_uid varchar(64) not null default auth.uid(),   -- 归属用户，默认取当前登录用户
  revision integer not null default 1,                 -- 乐观锁版本号，客户端按 baseRevision 比对
  deleted_at timestamptz,                              -- 软删除时间；非空即视为已删除
  server_updated_at timestamptz not null default now(),-- 服务端更新时间，供增量拉取与触发器刷新
  payload jsonb not null default '{}'::jsonb           -- 业务字段全集
);

-- 会话表：一行＝一个 session（含时长/次数等汇总），每组明细内嵌在 payload 中
create table if not exists public.p068_sessions (
  id varchar(64) primary key,                          -- 客户端生成的全局唯一 ID
  owner_uid varchar(64) not null default auth.uid(),   -- 归属用户，默认取当前登录用户
  revision integer not null default 1,                 -- 乐观锁版本号，客户端按 baseRevision 比对
  deleted_at timestamptz,                              -- 软删除时间；非空即视为已删除
  server_updated_at timestamptz not null default now(),-- 服务端更新时间，供增量拉取与触发器刷新
  payload jsonb not null default '{}'::jsonb           -- 业务字段全集（含明细数组）
);

-- ─────────────────────────────── 索引 ───────────────────────────────

-- 归属索引：所有查询都带 owner_uid 过滤（RLS 同列），避免全表扫描
create index if not exists p068_projects_owner_uid_idx on public.p068_projects (owner_uid);

-- 增量拉取索引：客户端按 server_updated_at > 上次水位 拉取
create index if not exists p068_projects_server_updated_at_idx on public.p068_projects (server_updated_at);

-- 归属索引（会话表同上）
create index if not exists p068_sessions_owner_uid_idx on public.p068_sessions (owner_uid);

-- 增量拉取索引（会话表同上）
create index if not exists p068_sessions_server_updated_at_idx on public.p068_sessions (server_updated_at);

-- ────────────────────────────── 权限授予 ──────────────────────────────

-- 只授予已登录角色 authenticated 的增删改查；不给 anon，匿名请求即便带密钥也写不进、读不到
grant select, insert, update, delete on public.p068_projects to authenticated;
grant select, insert, update, delete on public.p068_sessions to authenticated;

-- ──────────────────────────── 行级安全（RLS）────────────────────────────

-- 开启行级安全：授予权限后仍需策略放行，否则默认拒绝
alter table public.p068_projects enable row level security;
alter table public.p068_sessions enable row level security;

-- 项目表策略：只允许本人读写本人的行
drop policy if exists p068_projects_select_own on public.p068_projects;
create policy p068_projects_select_own on public.p068_projects
  for select to authenticated using (owner_uid = auth.uid());          -- 查询：只能看到自己的行

drop policy if exists p068_projects_insert_own on public.p068_projects;
create policy p068_projects_insert_own on public.p068_projects
  for insert to authenticated with check (owner_uid = auth.uid());     -- 插入：归属必须是本人

drop policy if exists p068_projects_update_own on public.p068_projects;
create policy p068_projects_update_own on public.p068_projects
  for update to authenticated
  using (owner_uid = auth.uid())                                        -- 更新目标：只能是自己的行
  with check (owner_uid = auth.uid());                                 -- 更新结果：归属仍必须是本人（防改归属）

drop policy if exists p068_projects_delete_own on public.p068_projects;
create policy p068_projects_delete_own on public.p068_projects
  for delete to authenticated using (owner_uid = auth.uid());          -- 删除：只能删自己的行

-- 会话表策略：同上四条
drop policy if exists p068_sessions_select_own on public.p068_sessions;
create policy p068_sessions_select_own on public.p068_sessions
  for select to authenticated using (owner_uid = auth.uid());

drop policy if exists p068_sessions_insert_own on public.p068_sessions;
create policy p068_sessions_insert_own on public.p068_sessions
  for insert to authenticated with check (owner_uid = auth.uid());

drop policy if exists p068_sessions_update_own on public.p068_sessions;
create policy p068_sessions_update_own on public.p068_sessions
  for update to authenticated
  using (owner_uid = auth.uid())
  with check (owner_uid = auth.uid());

drop policy if exists p068_sessions_delete_own on public.p068_sessions;
create policy p068_sessions_delete_own on public.p068_sessions
  for delete to authenticated using (owner_uid = auth.uid());

-- ─────────────────────── 服务端时间戳自动刷新（触发器）───────────────────────

-- 通用函数：任何 INSERT 或 UPDATE 都把 server_updated_at 置为 now()，
-- 保证客户端增量拉取的水位单调前进，不依赖客户端时钟。
create or replace function public.p068_touch_server_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.server_updated_at := now();
  return new;
end;
$$;

-- 项目表：插入与更新前刷新服务端时间戳
drop trigger if exists p068_projects_touch_updated_at on public.p068_projects;
create trigger p068_projects_touch_updated_at
  before insert or update on public.p068_projects
  for each row execute function public.p068_touch_server_updated_at();

-- 会话表：插入与更新前刷新服务端时间戳
drop trigger if exists p068_sessions_touch_updated_at on public.p068_sessions;
create trigger p068_sessions_touch_updated_at
  before insert or update on public.p068_sessions
  for each row execute function public.p068_touch_server_updated_at();

-- ─────────────────────────────── 回滚 ───────────────────────────────
-- 以下语句默认不执行；确需整体回滚时人工取消注释后执行（顺序：先触发器，再函数，最后表）。
--
-- drop trigger if exists p068_projects_touch_updated_at on public.p068_projects;
-- drop trigger if exists p068_sessions_touch_updated_at on public.p068_sessions;
-- drop function if exists public.p068_touch_server_updated_at();
-- drop table if exists public.p068_sessions;
-- drop table if exists public.p068_projects;