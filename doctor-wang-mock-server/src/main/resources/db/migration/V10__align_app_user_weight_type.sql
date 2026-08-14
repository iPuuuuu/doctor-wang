-- V10: 对齐 app_user.weight 与 JPA 实体契约（Float → float4）。
-- V1 创建 weight numeric(5,2)，实体映射 weight: Float，Hibernate ddl-auto: validate
-- 要求 float4；纠偏迁移（AGENTS.md：禁止修改既有 V1）。
alter table app_user alter column weight type real;
