# CloudBase PG 接入事实（调研）

> 调研方式：只读抓取腾讯云开发官方文档（docs.cloudbase.net），未修改代码、未运行构建/测试、未访问需登录的控制台。
> 调研时间：2026-11-10。
> 说明：每条结论后附来源 URL 与原文关键句；官方文档未写明的，统一标「待验证」，不推断为事实。
> 抓取限制：`cloud.tencent.com` 价格文档在本机抓取失败，计费细节仅以 docs.cloudbase.net 的《资源点计费FAQ》为准，价格页口径标「待验证」。

---

## 1. 环境与地域

### 1.1 新建环境时 PG 模式可选

结论：可以。创建环境时选择「PostgreSQL 数据库」，环境即以 PG 模式运行，无需再手动初始化数据库实例。

- 来源：https://docs.cloudbase.net/quick-start/create-env
  > 「创建环境时，如果选择 **PostgreSQL 数据库**，环境将以 PG 模式运行：以 PostgreSQL 作为统一底座，数据库、身份认证与云存储共用一套 SQL 权限模型。」
- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「当您创建 CloudBase PostgreSQL 版本环境时，PostgreSQL 数据库会**自动启用**，无需手动创建或初始化数据库实例。」

### 1.2 旧环境不能切换为 PG 模式

结论：不能。官方三处文档一致写明「仅新建环境支持，存量环境不支持升级」，`env-overview` 还额外写明「不可切换」。

- 来源：https://docs.cloudbase.net/quick-start/pg-overview
  > 「PG 模式**仅新建环境支持**，存量传统模式环境不支持升级。如需使用相关能力，请新建环境并选择 **PostgreSQL 数据库**。」
- 来源：https://docs.cloudbase.net/quick-start/create-env
  > 「PG 模式仅新建环境支持，存量环境不支持升级。」
- 来源：https://docs.cloudbase.net/quick-start/env-overview
  > 「**PG 模式仅新建环境支持**，存量环境不支持升级，**不可切换**」

### 1.3 如何判断一个环境是不是 PG 模式

结论：调用云 API `DescribeEnvs`，看返回的 `EnvInfo.PostgreSQL` 数组是否非空。

- 来源：https://docs.cloudbase.net/quick-start/env-overview
  > 「**`PostgreSQL` 数组非空**（包含实例信息）→ 该环境以 **PG 模式** 运行；**`PostgreSQL` 字段为空或不存在** → 该环境为**传统模式**。」
  > 示例字段：`"Region": "ap-shanghai"`, `"Version": "17.6"`

### 1.4 地域

结论：官方文档只明确出现两个地域：上海 `ap-shanghai`（国内）与新加坡 `ap-singapore`（海外）。其他地域是否存在、PG 模式在哪些地域可开，官方这几篇未给出完整列表，标「待验证」。

- 来源：https://docs.cloudbase.net/http-api/basic/overview
  > 「环境所属地域为上海（`ap-shanghai`）等**国内地域**时，使用如下域名：`https://{your-env}.api.tcloudbasegateway.com`」
  > 「环境所属地域为新加坡（`ap-singapore`）等**海外地域**时，使用如下域名：`https://{your-env}.api.intl.tcloudbasegateway.com`」
- 来源：https://docs.cloudbase.net/authentication-v2/method/email-login
  > 「`region: "ap-shanghai", // 不传默认为上海地域`」
- 待验证：PG 模式可选地域全集；`CreateEnv` 的 `Region` 允许值；国内/海外账号是否都能开 PG 模式。

---

## 2. Android Kotlin 官方接入方式

### 2.1 官方没有 Android 原生 SDK；Android Kotlin 页面走的是 HTTP API

结论：官方 SDK 清单只有小程序 ClientSDK、Web JS SDK、Node SDK、HTTP API，没有 Android/Kotlin 原生 SDK。所谓「Android Kotlin 快速开始」实际是「用 HTTP API + 自己封装的 `CloudBaseClient`」示例，页面中没有 Gradle 依赖坐标，也没有 SDK 初始化代码。

- 来源：https://docs.cloudbase.net/quick-start/frameworks/android-kotlin
  > 「**获取 API 访问凭证**：在 CloudBase 控制台 获取环境 ID 和 API 密钥」
  > 「更多详情请参考：Android Kotlin 完整文档 → `http-api/basic/overview`」
  > 示例代码全部是 `cloudbase.request(method, path, body, typeToken)` 形式的自写封装。
- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「云开发提供了多种 SDK 供开发者操作 PostgreSQL 数据库：小程序 ClientSDK / JS SDK / Node SDK / HTTP API」
  > 表格中「适用平台」分别为「小程序 / Web 浏览器 / Node.js 环境 / 通用」，无 Android。
- 结论补充：`CloudBaseClient` 在官方页面中未给出定义或依赖声明，属于示例作者自建；工程中需要自行用 OkHttp（或 Retrofit）+ JSON 库实现。
- 待验证：是否存在未列入导航的 Android 原生 SDK / Maven 坐标（本次未在官方文档中找到，按「无」处理）。

### 2.2 依赖与坐标

结论：官方未提供 Android 的 Maven/Gradle 坐标。示例代码用到的第三方库为 `okhttp3`、`com.google.gson`，但页面没有给出 `implementation(...)` 行。

- 来源：https://docs.cloudbase.net/quick-start/frameworks/android-kotlin
  > 示例 import：`import okhttp3.OkHttpClient`、`import okhttp3.Request`、`import com.google.gson.Gson`
  > 全文无 `implementation` / `gradle` / `maven` 片段。
- 待验证：官方是否发布了可用的 Android artifact；若自行实现，OkHttp/Gson 版本需按项目现状选型，不能声称是「官方 SDK」。

### 2.3 初始化参数

结论：Android 侧实际只需要三个连接参数：`envId`、`region`（决定国内/海外域名）、以及一个可放入客户端的 `Publishable Key`（或登录后的 `access_token`）。没有传统意义上的 `init(appId, secret)`。

- 来源：https://docs.cloudbase.net/http-api/basic/overview
  > 国内：`https://{your-env}.api.tcloudbasegateway.com`；海外：`https://{your-env}.api.intl.tcloudbasegateway.com`
  > 「`{your-env}` 替换为实际的环境 ID。」
- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「`const app = cloudbase.init({ env: '<env-id>', accessKey: '<Publishable Key>' });`」
- 来源：https://docs.cloudbase.net/quick-start/frameworks/android-kotlin
  > 「获取环境 ID 和 API 密钥」（**措辞有歧义，见 §3**）

### 2.4 登录方式

结论：PG 模式支持传统模式的全部登录方式：匿名、用户名密码、短信验证码、邮箱验证码、第三方、自定义登录。Android 无 SDK，因此全部通过 `/auth/v1/*` HTTP 接口完成。

- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「登录方式与 SDK 接入方式与传统模式完全一致……各类登录（匿名 / 用户名密码 / 短信 / 邮箱 / 第三方）请直接参考通用文档。」
  > 「登录方式 | 全部支持 | 全部支持」

#### 2.4.1 邮箱验证码（三步）

1. `POST /auth/v1/verification`，body `{"email": "...", "target": "NON_USER" | "ANY" | "USER"}`
2. `POST /auth/v1/verification/verify`，body `{"verification_id", "verification_code"}` → 得 `verification_token`
3. `POST /auth/v1/signin`（已注册）或 `POST /auth/v1/signup`（注册），body 带 `email + verification_token`

- 来源：https://docs.cloudbase.net/quick-start/frameworks/android-kotlin
  > 「步骤1: 发送邮箱验证码……`/auth/v1/verification`……`"target" to "NON_USER"`」
  > 「步骤2: 验证验证码……`/auth/v1/verification/verify`……`verification_token`」
  > 「步骤3: 使用验证令牌登录……`/auth/v1/signin`」
- 来源：https://docs.cloudbase.net/http-api/auth/auth-sign-in
  > 「邮箱验证码登录……`verification_token`: 验证码token，必填，通过验证短信、邮箱验证码接口获取」
  > 响应含 `access_token`、`refresh_token`、`expires_in: 7200`、`sub`
- 注意：内置邮件服务有每日发件数量限制；量大需配置自定义 SMTP。

#### 2.4.2 匿名登录

- `POST /auth/v1/signin/anonymously`，请求头必须带 `x-device-id`。
- 同一设备 ID 只能注册一个匿名用户；匿名用户可后续绑定其他登录方式升级为正式用户。
- 来源：https://docs.cloudbase.net/http-api/auth/auth-sign-in-anonymously
  > 「同一个设备ID最多只能注册一个匿名用户」
  > 「`x-device-id`: 设备id（必填，请求头中传入）」
  > 「可以通过绑定其他登录方式升级为正式用户」

#### 2.4.3 自定义登录

- 服务端用自定义登录私钥签发 Ticket，客户端拿 Ticket 登录；私钥**只能**放服务端。
- 来源：https://docs.cloudbase.net/authentication-v2/method/custom-login
  > 「签发 Ticket 的操作**仅能在服务端进行**（云函数、云托管、自建服务器等）。`credentials` 中的私钥是证明管理员身份的敏感凭证，严禁写入小程序、H5 等前端环境或前端可访问的环境变量中。」
  > 「客户端使用 Ticket 完成首次登录（此时 CloudBase 会自动创建对应用户）。」

### 2.5 Token 获取与刷新

结论：登录成功返回 `access_token` + `refresh_token`，`expires_in` 默认 7200 秒。Access Token 过期后用 `POST /auth/v1/token`、`grant_type=refresh_token` 换取新令牌；刷新后旧 refresh_token 立即失效（轮换）。

- 来源：https://docs.cloudbase.net/http-api/auth/auth-sign-in
  > 响应示例：`"access_token": "...", "refresh_token": "...", "expires_in": 7200, "sub": "..."`
- 来源：https://docs.cloudbase.net/http-api/auth/auth-grant-token
  > 「`grant_type`: 授权类型（必填，固定值为"refresh_token"）……`refresh_token`: 刷新令牌（必填）」
  > 「原refresh\_token使用后立即失效」「返回新的access\_token和refresh\_token」
- 来源：https://docs.cloudbase.net/authentication-v2/auth/token
  > 「Access Token 有效期 | 2 小时 | 1 - 24 小时 | 过期后需使用 Refresh Token 换取新令牌」
  > 「Refresh Token 有效期 | 30 天 | 1 小时 - 30 天 | 超过有效期后无法获取新 Access Token，需重新登录」
  > 「最大会话数 | 1 个 | 1 - 100 个 | 当会话数超过设定阈值时，系统将自动使最早的 Refresh Token 失效」

### 2.6 调用数据库的方式：Android 用 PostgREST HTTP，不是 SDK

结论：Android/Kotlin 没有 `app.rdb()` 这类 SDK 能力，只能走 PostgREST 风格的 REST 接口：

```
GET/POST/PATCH/DELETE https://{envId}.api.tcloudbasegateway.com/v1/rdb/rest/{table}
Authorization: Bearer <Publishable Key | access_token | API Key>
```

- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「REST:  `https://<envId>.api.tcloudbasegateway.com/v1/rdb/rest/<table>`」
  > 「适用于无 SDK 场景（其他后端、低代码平台、第三方系统）。」
  > 「SDK 请求**必须携带 JWT**（Publishable Key、access\_token 或 API Key 三选一）。未提供时网关会拒绝请求。」
- 来源：https://docs.cloudbase.net/http-api/pgdb/postgresql-restful-api
  > 「请求域名格式为：`https://{envId}.api.tcloudbasegateway.com/v1/rdb/rest/{table}`」
  > 「调用以下接口需要传递 AccessToken，格式如`Authorization: Bearer <token>`」
  > `Prefer: return=representation` / `count=exact` / `resolution=merge-duplicates` 等写操作选项
- 待验证：是否有社区维护的 Kotlin/Java PostgREST client 可复用；官方未推荐，自行实现时需自测过滤语法、错误码与分页头。

---

## 3. 客户端可内嵌的凭证（EnvId / Publishable Key / API Key）

### 3.1 三者区别

| 名称 | 本质 | 角色 | 是否过期 | 能否进 APK |
|---|---|---|---|---|
| EnvId | 环境 ID，非密钥 | 无 | — | ✅ 可以 |
| Publishable Key | JWT，`role=anon` | `anon` | 不过期 | ✅ 可以（官方明确） |
| Access Token | 用户登录后颁发 | `authenticated` | 默认 2 小时 | ✅ 可以（运行时内存/加密存储） |
| API Key | JWT，`role=service_role`，BYPASSRLS | `service_role` | 不过期 | ❌ 绝对不可以 |
| SecretId/SecretKey | 腾讯云账号 API 密钥 | 管理员 | — | ❌ 绝对不可以 |
| 自定义登录私钥 | RSA 私钥 | 签发 Ticket | — | ❌ 绝对不可以 |

- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「**Publishable Key** | `anon` | 前端直接访问 | ❌ 不过期 | ✅ 可以安全嵌入前端代码」
  > 「**Access Token** | `authenticated` | 已登录用户访问数据 | ✅ 默认 2 小时 | 由 SDK 自动管理与刷新」
  > 「**API Key** | `service_role` | 后端管理员操作 | ❌ 不过期 | ⚠️ **严禁前端**，通过环境变量注入」
  > 危险提示：「`API Key`（对应 `service_role`）拥有绕过 RLS 的特权，**绝不能暴露在前端代码、小程序、App 中**。」
- 来源：https://docs.cloudbase.net/service/authentication
  > 「**API Key** 拥有管理员权限，严禁在客户端使用，仅用于服务端环境」
  > 「**Publishable Key** 可以安全地暴露在浏览器中，用于请求公开访问的资源」
- 来源：https://docs.cloudbase.net/http-api/basic/access-token
  > 「ApiKey 严禁在客户端使用。」

### 3.2 官方文档存在措辞歧义，必须按 PG 文档执行

`android-kotlin` 页面写「获取环境 ID 和 API 密钥」，但 `auth-pg` / `service/authentication` 明确 `API Key` 是 `service_role`、严禁进 App。两者冲突时，应以 PG 认证文档为准：**Android 客户端只放 Publishable Key，不放 API Key**。

- 来源：https://docs.cloudbase.net/quick-start/frameworks/android-kotlin
  > 「获取环境 ID 和 API 密钥」
- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「API Key（对应 `service_role`）……**绝不能暴露在前端代码、小程序、App 中**」

### 3.3 Publishable Key 不能当用户身份

- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「Publishable Key 的 `sub` 字段是固定字符串 `"anon"`，不是某个真实用户 ID；调用 `/auth/v1/signin/anonymously` 进行**匿名登录**后获得的 Access Token，`role` 仍然是 `anon` 但**会有真实的 `sub`**（匿名用户 ID），可作为业务数据归属字段使用。」

结论：只带 Publishable Key 时，所有数据归属都会落到 `"anon"`；要区分用户，必须做匿名登录或正式登录拿真实 `sub`。

---

## 4. PG 模式下 auth 用户 ID 的类型与获取

### 4.1 类型

结论：`auth.users.id` 是 `VARCHAR(64)`，与 JWT 的 `sub` 完全一致；业务归属列建议 `varchar(64)`（或 `text`）。

- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「`auth.users` 主键 `id` 字段类型为 `VARCHAR(64)`，与 JWT 中的 `sub` claim **完全一致**。」
  > 「业务表归属字段建议保持与 `auth.users.id` 相同类型（`varchar(64)`，也可使用 `text`，二者在 PostgreSQL 中可互相赋值）」

### 4.2 SQL 中获取

| 函数 | 返回 | 等价写法 |
|---|---|---|
| `auth.uid()` | `text` | `auth.jwt() ->> 'sub'` |
| `auth.role()` | `text` | `auth.jwt() ->> 'role'` |
| `auth.email()` | `text` | `auth.jwt() ->> 'email'` |
| `auth.jwt()` | `jsonb` | `request.jwt.claims` GUC |

- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「`auth.uid()` | `text` | 当前用户 ID，等价于 `auth.jwt() ->> 'sub'`」
  > 「网关在转发请求到 PostgREST 前，会把 JWT claims 注入到数据库会话的 GUC 变量 `request.jwt.claims`」

### 4.3 列默认值自动归属

```sql
CREATE TABLE public.orders (
  id        bigserial PRIMARY KEY,
  buyer_id  varchar(64) NOT NULL DEFAULT auth.uid(),
  amount    numeric(10,2),
  created_at timestamptz DEFAULT now()
);
```

- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「关键：`buyer_id` 使用 JWT `sub` 作为默认值，**不依赖前端传入**，从源头杜绝身份伪造。」

---

## 5. 建表 / RLS / GRANT 标准做法

### 5.1 标准顺序

1. 在 `public` 建表，归属列 `varchar(64) default auth.uid()`；
2. `GRANT` 表级权限给 `anon` / `authenticated`（按最小权限）；
3. `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`；
4. 为 SELECT / INSERT / UPDATE / DELETE 分别建 Policy；
5. UPDATE 必须同时写 `USING` 与 `WITH CHECK`；
6. 用 `serial`/`bigserial` 时补 `GRANT USAGE, SELECT ON SEQUENCE ...`。

- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「请求 → JWT 解析 → 第一层：表级 GRANT（能执行哪些操作）→ 第二层：RLS Policy（能访问哪些行）→ 返回结果」
  > 「**双重锁定原则**：只有表级 GRANT 与 RLS Policy **都通过**，操作才会成功。任一层拒绝均返回权限错误。」
- 来源：https://docs.cloudbase.net/authentication-v2/auth/auth-pg
  > 「`service_role` 默认即可访问所有表（具备 BYPASSRLS）。`anon` 与 `authenticated` 必须通过 GRANT 显式授权才能访问。」
  > 「`USING` 控制"能操作哪些行"，`WITH CHECK` 控制"更新后的值是否合法"，**二者配合防止用户通过 UPDATE 篡改 `buyer_id` 窃取数据**。」

### 5.2 可直接复用的最小模板

```sql
-- 表
create table public.todos (
  id bigint generated by default as identity primary key,
  user_id varchar(64) not null default auth.uid(),
  title text not null,
  done boolean not null default false,
  created_at timestamptz not null default now()
);

-- 表级权限
grant select, insert, update, delete on public.todos to authenticated;

-- 行级权限
alter table public.todos enable row level security;

create policy todos_select_own on public.todos
  for select to authenticated using (user_id = auth.uid());

create policy todos_insert_own on public.todos
  for insert to authenticated with check (user_id = auth.uid());

create policy todos_update_own on public.todos
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
```

- 来源：https://docs.cloudbase.net/database/postgresql/tables
  > 建表示例：`user_id varchar(64) not null default auth.uid()`
  > 「主键 | 使用 `bigint generated by default as identity` 或 `uuid`」
  > 「`timestamptz` | 带时区时间，建议用于创建时间和更新时间」

### 5.3 常见陷阱（官方列出）

- 启用 RLS 但没建 Policy → 非 `service_role` 请求全被拒；
- 建了 Policy 但没 GRANT → 表级权限不通过；
- 前端暴露 API Key → 所有数据暴露；
- UPDATE 只写 `USING` 不写 `WITH CHECK` → 可篡改归属字段；
- 匿名用户给过多权限（`anon` 只给 SELECT）。

来源：https://docs.cloudbase.net/database/postgresql/initialization

### 5.4 执行 DDL 的通道

- 控制台 SQL 编辑器，或云 API `ExecutePGSql`（管理员、后端使用）。
- 注意：`ExecutePGSql` 每次只能执行一条 SQL；部分 DDL 直接执行会 `InternalError`，需包成 `DO LANGUAGE plpgsql $$ BEGIN EXECUTE '...'; END $$`。
- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「**每次 API 调用只能执行一条 SQL**，不能用分号拼接多条」
  > 「部分 DDL……在通过 ExecutePGSql 直接执行时可能返回 `InternalError`。**遇到失败时**，可将该 DDL **包装为匿名代码块**重试。」

---

## 6. 自定义 schema 能否通过客户端 API 访问

结论：**不能**。官方明确 REST/PostgREST 只支持 `public` schema，`auth`、`storage` 及其他自定义 schema 均不可通过客户端 REST 访问；只能改用云 API `ExecutePGSql` 或控制台 SQL 编辑器（均为管理面，不能进 App）。

- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 「⚠️ 注意：REST API 目前只支持 `public` schema，只能访问 `public` schema 下的表、视图和函数；`auth`、`storage` 等其他 schema 无法通过 REST API 访问。」
- 来源：https://docs.cloudbase.net/http-api/pgdb/postgresql-restful-api
  > 「仅支持 public schema：目前 REST API 只支持 `public` schema……不支持访问或切换到其他 schema（如 `auth`、`storage`）。」
- 待验证：是否存在平台侧开关/白名单可把自定义 schema 暴露给 PostgREST（官方文档未提；按「不支持」设计，不要依赖）。

---

## 7. 计费、额度与到期规则

### 7.1 套餐与资源点

| 套餐 | 价格 | 资源点 |
|---|---|---|
| 免费体验版 | 0 元/月 | 3,000 点 |
| 个人版 | 19.9 元/月 | 40,000 点 |
| 标准版 | 199 元/月 | 330,000 点 |
| 企业版 | 999 元/月 | 1,500,000 点 |

- 来源：https://docs.cloudbase.net/quick-start/resource-point
  > 「**免费体验版**：0 元/月（3,000 点）……**个人版**：19.9 元/月（40,000 点）……**标准版**：199 元/月（330,000 点）……**企业版**：999 元/月（1,500,000 点）」

### 7.2 换算口径

- 「1000 点 ≈ 1 元」；
- 数据库调用 200 点/万次；
- 云函数调用 13 点/万次；
- CDN 流量 210 点/GB；
- 短信 50 点/条；
- 超限按 1:1 扣费，扣费顺序：套餐资源点 → 资源包 → 按量付费；资源包有效期 1 年。

- 来源：https://docs.cloudbase.net/quick-start/resource-point
  > 「标准版套餐的换算比例如下（1000 点 ≈ 1 元）……数据库调用 200 点 / 万次」
  > 「超限部分按 **1:1** 的比例扣费（即 1000 点 = 1 元）……扣费顺序为：**套餐资源点 → 资源包 → 按量付费**。」
  > 「余额低于 10% 时，系统会通过邮件和控制台发送告警通知。」

**对本项目的直接含义**：免费版 3,000 点 ≈ 15 万次数据库调用/月（3000 ÷ 200 × 10000）。但每次「全量拉取」会按返回行数/请求计费，实际计费粒度文档未展开，标「待验证」；按最保守口径也应做增量。

### 7.3 切换、到期与付费

- 资源点计费切换是**单向**，不能退回旧固定配额模式；
- 免费体验环境**不能**直接切换，需先升级付费套餐；
- 旧套餐/微搭套餐需先升级到现网套餐；
- 新用户有「开发期免费环境」，开发免费、正式使用需付费；腾讯云侧一个账号可创建一个免费环境。

- 来源：https://docs.cloudbase.net/quick-start/resource-point
  > 「**不能。** 切换是单向操作，一旦转为资源点计费，将无法回退到旧的固定配额模式。」
  > 「处于**免费体验环境**的用户（需先升级为付费套餐）……不支持直接切换」
- 来源：https://docs.cloudbase.net/quick-start/create-env
  > 「云开发为新用户提供开发期免费环境，开发免费、正式使用需付费。但免费环境存在一些功能使用限制」
  > 「当前在微信侧……可创建两个免费环境，在腾讯云侧可创建一个免费环境。」
- 待验证：免费体验版到期后的具体行为（自动停服/只读/宽限期/是否自动转按量）、续费与欠费规则、PG 实例单独计费项；官方这 9 篇未写明，需以控制台下单页与价格文档为准。

---

## 8. PG 备份能力

结论：有自动备份、手动备份、时间点恢复；恢复「至原实例」不会覆盖当前业务库，而是新建一个库 `postgres_{唯一标识}_bak_{恢复时间戳}`；另可用 `pg_dump` 做逻辑导出补充。

- 来源：https://docs.cloudbase.net/database/postgresql/backup
  > 「常见备份能力包括自动备份和手动备份……时间点恢复：根据备份和日志恢复到指定时间点，具体支持范围以控制台为准。」
  > 「选择**恢复至原实例**时，数据不会覆盖当前的业务数据库，而是恢复到一个新建的数据库中，库名规则为：`postgres_{唯一标识}_bak_{恢复时间戳}`」
  > 「对于需要长期留存或跨环境迁移的数据，可以使用 `pg_dump` 导出逻辑备份。」
- 来源：https://docs.cloudbase.net/database/postgresql/faq
  > 「PostgreSQL 数据库的备份与恢复由腾讯云 PostgreSQL 服务（云数据库 PostgreSQL）底层提供支持，包括自动备份和手动备份。」
- 待验证：免费体验版是否包含备份、保留天数、PITR 可回溯窗口、备份是否额外计费——文档统一写「以控制台展示为准」。

---

## 9. 离线优先 App 需要的增量同步能力

### 9.1 `updated_at`：需要自己建，官方不自动提供

结论：官方建表建议里没有自动维护 `updated_at` 的机制说明；只有「`timestamptz` 建议用于创建时间和更新时间」的类型建议。要实现增量同步，必须自行加列，并用触发器或客户端写入维护。

- 来源：https://docs.cloudbase.net/database/postgresql/tables
  > 「`timestamptz` | 带时区时间，建议用于创建时间和更新时间」
  > 建表模板只含 `created_at timestamptz not null default now()`，无 `updated_at` 自动更新。
- 待验证：平台是否预置了 `moddatetime`/`set_updated_at` 之类的触发器扩展。文档未提；按「无」设计，自己写 `BEFORE UPDATE` 触发器最稳。

### 9.2 游标 / 增量拉取：官方没有专用同步 API

结论：没有「sync / changes / cursor」类接口。只能借 PostgREST 的过滤与排序能力自己实现，例如：

```
GET /v1/rdb/rest/entries?select=*&user_id=eq.<uid>&updated_at=gt.<lastSync>&order=updated_at.asc&limit=200
```

- 来源：https://docs.cloudbase.net/database/postgresql/initialization
  > 查询表达式支持：`?price=gte.1000&price=lte.5000`、`?order=price.desc`、`?limit=10&offset=0`、`?select=id,name,price`
- 来源：https://docs.cloudbase.net/http-api/pgdb/postgresql-restful-api
  > `Prefer: count=exact` 返回 `Content-Range`；`limit/offset` 分页
- 结论：过滤/排序/分页是 PostgREST 通用能力，不等于官方提供了「增量同步协议」；断点续传、冲突解决、删除墓碑都要自己做。
- 待验证：`updated_at` 相同值批量写入时的稳定排序（需加次级排序键如 `id`），以及 `gt` 过滤是否走索引（需自建 `(user_id, updated_at)` 索引）。

### 9.3 实时订阅：存在，但是 Private Alpha + 仅 JS SDK + 不保证 SLA

结论：有 `app.realtime()` 的 Postgres Changes（INSERT/UPDATE/DELETE 推送），但：

1. 处于 **Private Alpha**，仅受邀用户可开，需申请；
2. 接口/参数/控制台行为可能不兼容变更；
3. **不提供 SLA，官方明确「请勿用于生产链路」**；
4. 客户端只支持浏览器、Node.js、微信小程序（JS SDK），**没有 Android/Kotlin 原生支持**。

- 来源：https://docs.cloudbase.net/database/postgresql/realtime
  > 「实时推送目前处于 **Private Alpha** 阶段：功能仍在开发中，仅对受邀用户开放，需申请开通后使用……**不提供 SLA，请勿用于生产链路**。」
  > 「浏览器、Node.js 和微信小程序均可通过 JavaScript SDK 的 `app.realtime()` 使用。」
  > 「Postgres Changes | PostgreSQL 表发生 `INSERT`、`UPDATE` 或 `DELETE` 后，将变更推送给订阅者」
  > 「目标表已加入 `cloudbase_realtime` 发布」「订阅角色对目标表有 `SELECT` 权限」「目标表已开启 RLS」
- 结论：对原生 Android 的离线优先同步，实时推送**不能作为主链路**；可作为未来可选增强，且需自行在 Android 侧实现 WebSocket 协议（官方未提供客户端，协议细节标「待验证」）。

### 9.4 事务：REST/SDK 不直接支持

- 来源：https://docs.cloudbase.net/database/postgresql/faq
  > 「云开发 PostgreSQL SDK 目前不直接支持事务操作。如需使用事务功能，推荐以下方案：1. **使用 RPC 调用**……2. **使用 pg 协议直连**」
- 结论：多表原子写入（例如 session + rep_sets + 同步状态）不能靠一次 REST 调用完成；要么用数据库函数（RPC）封装，要么接受最终一致 + 幂等重试。

### 9.5 幂等 upsert

- PostgREST 支持 `Prefer: resolution=merge-duplicates` / `ignore-duplicates`，可配合主键做 upsert，适合离线补传去重。
- 来源：https://docs.cloudbase.net/http-api/pgdb/postgresql-restful-api
  > 「`resolution=merge-duplicates` upsert操作，合并冲突项」「`resolution=ignore-duplicates` upsert操作，忽略冲突项」

---

## 10. 待验证清单（不要在实现中当作事实）

1. PG 模式可选地域全集；腾讯云侧账号是否都能开 PG。
2. 免费体验版到期后的停服/只读/宽限期/自动续费规则；PG 实例是否单独计费。
3. 免费体验版是否包含备份、保留天数与 PITR 窗口。
4. 数据库调用的实际计费粒度（按请求数还是返回行数/数据量）。
5. 是否存在未公开的 Android 原生 SDK 或 Maven 坐标。
6. 自定义 schema 是否有平台开关可暴露给 PostgREST。
7. Realtime 的 WebSocket 协议是否能被非 JS 客户端直接复用；Android 原生接入是否在路线图中。
8. `ExecutePGSql` 在国内各地域的可用性与限流。
9. 邮箱验证码内置邮件服务的每日限额具体数值。
10. Publishable Key 是否可按环境/来源限制（安全域名、包名白名单）——官方 PG 文档只写「可安全嵌入」，未写 Android 包名绑定。

---

## 对 P068 的直接影响

| 项 | 事实 | 对本项目的影响 |
|---|---|---|
| 环境创建 | PG 模式仅新建环境支持，存量环境不可切换 | V1.2 探针必须先确认目标是新建 PG 环境；若用户已有传统环境，只能新建，不能原地升级，环境 ID/域名/账单都会变 |
| Android 接入层 | 官方无 Android 原生 SDK，Android Kotlin 页面走 HTTP API + 自写 `CloudBaseClient` | 需要新增 OkHttp（或 Retrofit）+ JSON 序列化依赖与一层 `CloudBaseApi` 封装；不能照搬 JS SDK 的 `app.rdb()` 写法，工期与自测成本要按「自建客户端」估 |
| 客户端凭证 | 只有 EnvId + Publishable Key 可进 APK；API Key/SecretKey/自定义登录私钥绝对不可 | APK 内置配置只能是 `envId + publishableKey + region`；CI/打包脚本要做密钥扫描；Android 文档「API 密钥」措辞不能照抄，必须取 Publishable Key |
| 身份与归属 | `auth.users.id` 为 `VARCHAR(64)`，等于 JWT `sub`；`auth.uid()` 返回 `text`；Publishable Key 的 `sub` 固定为 `"anon"` | 本地 UUID 主键可直接映射为 `varchar(64)`；业务表 `user_id` 用 `default auth.uid()`；若不做登录，所有数据都会归到 `"anon"`，多设备无法区分用户，需先定「匿名登录 / 邮箱登录 / 暂不区分」 |
| 登录链路 | 邮箱验证码需 3 步 HTTP；匿名登录需 `x-device-id` 且一设备一账号 | 若引入账号，需要新增验证码发送/校验/登录/刷新四条链路与失败态 UI；匿名登录要生成并持久化设备 ID；这会改变当前「纯本地、无网络权限」的产品形态，属于产品决策而非纯技术接入 |
| 数据模型 | `timestamptz` 适合 `created_at/updated_at`；官方不自动维护 `updated_at` | 现有 `rep_sets` 无 `updatedAt`、`projects`/`rep_sets` 缺墓碑，必须补 `updated_at`（触发器或客户端写）+ `deleted_at`/tombstone，否则增量拉取无法识别更新与删除 |
| 增量同步 | 无官方游标/同步 API，只能用 PostgREST `updated_at=gt.<cursor>&order=updated_at.asc&limit=N` 自建 | 需要设计 `(user_id, updated_at, id)` 复合游标与索引，处理同时间戳分页、删除墓碑、冲突合并；不能声称「用 CloudBase 的同步能力」 |
| 事务与批量写 | REST/SDK 不直接支持事务；可用 RPC 或 pg 直连；PostgREST 支持 merge-duplicates upsert | 「一次训练 = session + 多条 rep_sets」不能原子提交；建议以 session 为主记录、rep_sets 幂等 upsert，或用数据库函数做服务端事务；离线补传必须幂等，避免重复行 |
| 权限配置 | 必须 GRANT + RLS 双层同时通过；REST 只支持 `public` schema | 所有同步表放 `public`；上线前必须跑「GRANT 缺失」「RLS 未建 Policy」「UPDATE 缺 WITH CHECK」三类反向自检，否则表现为 401/403 或查询永远为空 |
| 实时能力 | Realtime 为 Private Alpha、仅 JS SDK、无 SLA、明确不建议生产 | 不能把实时订阅作为 V1.2 主同步链路；Android 侧按「启动/前台/手动」拉取增量，实时推送列为后续可选增强 |
| 成本 | 免费版 3,000 点/月；数据库调用 200 点/万次；超限 1:1 计费 | 免费版可支撑早期探针，但全量拉取会放大调用与流量成本；必须做增量 + 限频 + 分页上限，并在设置页暴露同步频率/流量提示 |
| 备份 | 有自动/手动备份与 PITR；恢复到新库，不覆盖原库 | 云同步上线前应先确认免费版备份策略；「恢复演练」要纳入发布检查，不能把云端当唯一副本 |

---

## 来源清单

1. https://docs.cloudbase.net/quick-start/pg-overview
2. https://docs.cloudbase.net/quick-start/create-env
3. https://docs.cloudbase.net/authentication-v2/auth/auth-pg
4. https://docs.cloudbase.net/database/postgresql/initialization
5. https://docs.cloudbase.net/quick-start/frameworks/android-kotlin
6. https://docs.cloudbase.net/http-api/basic/overview
7. https://docs.cloudbase.net/service/authentication
8. https://docs.cloudbase.net/database/postgresql/faq
9. https://docs.cloudbase.net/quick-start/resource-point
10. https://docs.cloudbase.net/quick-start/env-overview
11. https://docs.cloudbase.net/http-api/basic/access-token
12. https://docs.cloudbase.net/http-api/auth/auth-sign-in
13. https://docs.cloudbase.net/http-api/auth/auth-sign-in-anonymously
14. https://docs.cloudbase.net/http-api/auth/auth-grant-token
15. https://docs.cloudbase.net/authentication-v2/auth/token
16. https://docs.cloudbase.net/authentication-v2/method/email-login
17. https://docs.cloudbase.net/authentication-v2/method/custom-login
18. https://docs.cloudbase.net/database/postgresql/tables
19. https://docs.cloudbase.net/database/postgresql/backup
20. https://docs.cloudbase.net/database/postgresql/realtime
21. https://docs.cloudbase.net/http-api/pgdb/postgresql-restful-api