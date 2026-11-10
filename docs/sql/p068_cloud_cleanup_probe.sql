-- P068 清理探针：删除连通性/RLS 验证用的临时探针表
-- 背景：v12_probe.sql 在 public schema 建过 p068_probe_sessions，仅用于验证 PG + RLS + REST 链路。
-- 本文件只删这一张探针表，不触碰正式表 p068_projects / p068_sessions。
-- 在 CloudBase 控制台 SQL 编辑器执行；DROP TABLE 会连带删除其索引与策略，无需额外清理。

drop table if exists public.p068_probe_sessions;