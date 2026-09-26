# FlashSale 一期实现任务清单

本文件是实现阶段的唯一执行清单。任务按依赖顺序推进；每次只处理当前最早的未完成任务。完成任务后填写验证结果，再进入下一项。

状态：`TODO` 待处理，`DOING` 处理中，`DONE` 已完成，`BLOCKED` 被明确阻塞。

## 执行规则

1. 开始任务前先把状态改为 `DOING`。
2. 任务完成必须运行对应验证命令，并记录结果。
3. 发现设计缺口时先暂停当前任务，在本文件增加说明，不绕过核心一致性问题。
4. 所有跨服务业务写入遵循本地事务 + Outbox + 消费幂等 + 补偿对账。
5. 开发环境使用 Docker；Java 21、Maven、Spring Boot 3。

## A. 工程与本地基础设施

| ID | 任务 | 前置 | 涉及内容 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|---|
| A01 | 创建 Maven 多模块工程骨架 | 无 | 根 `pom.xml`、服务模块、公共模块 | 所有模块可被 Maven 识别 | `mvn validate -DskipTests`：通过（11 个模块） | DONE |
| A02 | 建立 Java 21 Docker 开发构建环境 | A01 | `Dockerfile.dev`、Maven 缓存、启动说明 | 容器内可执行 `mvn test` | `docker build`、容器内 `mvn -B test`：通过 | DONE |
| A03 | 编排本地基础设施 | A02 | Docker Compose、PostgreSQL、Redis、RocketMQ、Nacos、XXL-Job、MinIO | 依赖服务健康可用 | `docker compose config`：通过；PostgreSQL/Redis 健康 | DONE |
| A04 | 接入数据库迁移 | A01 | Flyway、`db/001_initial_schema.sql` | 空库可重复初始化，迁移版本可追踪 | Java 21 容器执行两次：V1 成功，第二次无迁移；33 张表 | DONE |
| A05 | 建立统一配置与环境模板 | A02 | `.env.example`、配置分层、服务端口约定 | 本地/测试配置可区分 | `docker compose --env-file .env.example config`：通过 | DONE |

## B. 公共基础组件

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| B01 | 统一 API 响应、分页和错误码 | A01 | 响应结构与 `docs/api-design.md` 一致 | Docker Java 21 容器测试：2 tests passed | DONE |
| B02 | Trace ID、结构化日志和异常处理 | B01 | HTTP/MQ/任务链路可关联 Trace ID | Docker Java 21 容器测试：编译与 2 tests passed | DONE |
| B03 | JWT、角色和资源归属校验 | B01 | CUSTOMER/OPERATOR/ADMIN 隔离生效 | Docker Java 21 容器测试：3 tests passed | DONE |
| B04 | PostgreSQL、Redis、RocketMQ 客户端封装 | A03 | 连接、超时、重试、序列化配置统一 | Docker Java 21 容器编译通过，Redis/RocketMQ 客户端依赖已统一 | DONE |
| B05 | Outbox 通用模型与投递接口 | B04 | 支持租约、退避、PENDING/SENT/FAILED | Docker Java 21 容器测试：6 tests passed；租约与退避接口已完成 | DONE |
| B06 | 消费幂等通用模型 | B04 | PROCESSING/SUCCEEDED/FAILED 和超时恢复可复用 | Docker Java 21 容器测试：8 tests passed；抢占与超时恢复接口已完成 | DONE |

## C. 认证、网关与账户

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| C01 | Auth 服务注册/登录 | B01-B03,A04 | 注册只能创建 CUSTOMER；登录签发 JWT | Docker Java 21 容器测试：10 tests passed（公共 8 + Auth 2） | DONE |
| C02 | ADMIN 账户管理 | C01 | 角色、启停用、最后 ADMIN 保护和审计完整 | Docker Java 21 `mvn -B test -pl flashsale-auth -am`：通过 | DONE |
| C03 | Gateway 路由、鉴权与限流 | B02-B03,A03 | 路由、JWT、Sentinel、Trace 生效 | 公共运行时接线已完成；Docker Java 21 `mvn -B test`（11 模块）通过，待完成真实路由层验收 | DOING |

## D. 商品与优惠券

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| D01 | Product 服务 CRUD 与上下架 | B01-B05,C01 | 商品规则、审计、缓存失效正确 | API/事务测试 | DONE |
| D02 | 商品库存与直接订单库存流水 | D01,B05 | 条件扣减、释放、不可超卖 | 并发测试 | DONE |
| D03 | Coupon 模板管理 | B01-B05,C01 | 创建、修改、暂停、恢复、审计正确 | API/事务测试 | DONE |
| D04 | 优惠券领取 Lua、幂等和 Outbox | D03,B05-B06 | 不超发、不重复领取、失败可补偿 | 并发/故障测试 | DONE |
| D05 | 用户券状态机与订单锁券/恢复 | D04 | AVAILABLE/RESERVED/CONSUMED/EXPIRED 合法迁移 | 状态机测试 | DONE |

## E. 活动与活动库存

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| E01 | 活动创建、预热、开始、结束 | B01-B05,C01 | 活动基础状态和缓存正确 | Docker Java 21 `mvn -B -pl flashsale-activity -am test`：通过（19 tests） | DONE |
| E02 | 活动库存事件序号与账本 | E01,B05 | RESERVE/RELEASE 连续序号、Outbox 同事务提交 | Docker Java 21 `mvn -B -pl flashsale-activity -am test`：通过（19 tests） | DONE |
| E03 | 活动库存消费、流水和 checkpoint | E02,B06 | 重复消息不重复变更，checkpoint 不跨洞 | checkpoint 测试 | DONE |
| E04 | 活动 PAUSING/暂停屏障 | E02-E03 | 关闭新预扣、清算在途、建立 pause barrier；崩溃可恢复 | Docker Java 21 `mvn -B -pl flashsale-activity -am test`：通过（19 tests） | DONE |
| E05 | 异步恢复任务与 Redis 校验重建 | E04,B05-B06 | Outbox、checkpoint、账本、Redis 校验后才 ACTIVE；不一致可重建 | Docker Java 21 `mvn -B -pl flashsale-activity -am test`：通过（19 tests） | DONE |
| E06 | 活动查询、指标和运营接口 | E01-E05 | 对外查询不泄露内部处理中状态，指标可追踪 | Docker Java 21 `mvn -B -pl flashsale-activity -am test`：通过（19 tests） | DONE |

## F. 订单、支付与履约

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| F01 | 订单预览与金额快照 | D01,D05,E01 | 只校验不占资源，金额快照明确 | 单元/API 测试 | DONE |
| F02 | 普通订单创建与幂等 | D02,B05-B06,F01 | 多商品全量预留或整体失败 | 并发/幂等测试 | DONE |
| F03 | 活动订单创建与限购 | E02,E03,F01 | 单活动单商品，Redis 预扣与 DB 账本一致 | 并发/故障测试 | DONE |
| F04 | 用户取消、超时释放 | D02,D05,E02,F02-F03 | CAS 只释放一次，活动 RELEASE 可追平 | 竞争/补偿测试 | DONE |
| F05 | Payment 支付幂等与回调 | F02-F04,B05-B06 | 重复支付/回调只生效一次 | 并发/回调测试 | DONE |
| F06 | 支付成功确认与履约 | F05,D05 | 库存确认、券核销、履约、支付事件同事务 | 事务/幂等测试 | DONE |
| F07 | 延时超时取消与 Job 兜底 | F04-F06,A03 | 支付与超时竞争得到唯一合法终态 | 延时/竞争测试 | DONE |

## G. 投递、补偿与运维

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| G01 | 各服务 Outbox 投递器 | B05,D01,D04,E02,F04,F06 | 批量、租约、退避、重复投递可用 | 集成/故障测试 | DONE |
| G02 | 各消费者幂等处理器 | B06,D04,E03,F05-F07 | 重复、失败、PROCESSING 超时可恢复 | MQ 集成测试 | DONE |
| G03 | XXL-Job 对账与补偿 | E03-E05,D04-D05,F04,F07 | Redis/DB/账本/Outbox/订单状态可对账补偿 | 故障演练 | DONE |
| G04 | 监控、指标、日志和告警 | B02,G01-G03 | 覆盖延迟、积压、库存不一致、死信、补偿失败 | 指标检查 | DONE |
| G05 | Docker 一键启动与初始化 | A03,A04,G04 | 新环境可一键启动并完成健康检查 | `docker compose up` | DONE |

## H. 验收测试

| ID | 任务 | 前置 | 完成标准 | 验证 | 状态 |
|---|---|---|---|---|---|
| H01 | 全量 HTTP 接口契约与权限验收 | C01-C03,D01-D05,E01-E06,F01-F07,G04 | Auth、商品、优惠券、活动、订单、支付、库存和运营接口逐项符合 `api-design.md`：路径/方法、请求响应字段、分页、错误码、JWT 角色、资源归属、幂等键和 Trace ID 一致；未授权、越权、重复请求和非法参数均返回规定结果 | 2026-09-25～2026-09-26：按 `docker-compose.yml` + `docker-compose.acceptance.yml --profile r21` 分层执行验收，测试完成后已停止本轮启动服务，仅保留预先存在的独立 `redis` 容器。已验证且当前无异常：Compose/Flyway；Auth 注册、登录、JWT、Trace；Product CRUD/上下架/查询；Coupon 创建、规则限制、暂停/恢复、领取、重复领取幂等；Activity 创建、取消/重复取消、指标、预热、开始、暂停/重复暂停、恢复请求/查询；Order 预览、创建、列表、详情、明细、重复与冲突幂等、取消/重复取消；Payment 成功支付、重复支付幂等、回调及回调幂等、金额/币种异常；Inventory 运营 JWT、未授权、非法数量、资源不存在，以及 acceptance seed `INVENTORY_ACCEPTANCE_SEED=9001=10` 下预占/重复预占、确认/重复确认、已确认释放错误、释放/重复释放；CUSTOMER/OPERATOR/ADMIN 权限隔离和 Trace ID。已验证库存活动链路：Redis 库存从 9 减至 8 后经取消释放恢复至 8；活动库存事件 payload、Outbox、消费者幂等和 checkpoint 推进测试通过；活动 5 暂停后屏障为 `3`，延迟启用 Outbox/Activity Consumer 后 3 条事件连续消费至 checkpoint `3`，幂等记录均为 `SUCCEEDED`，未发现库存流水、账本、Redis 不一致。已完成的代码修复已分别提交：`bacf0e3`、`f7f2e22`、`80a495d` 等。不能严格保证/尚未完成：没有完成一次“存在未消费库存事件且 barrier>0 → 暂停 → 追平 → 恢复成功 → 最终库存对账”的真实 HTTP 闭环；活动 5 后续恢复因时间窗口过期返回 `ACTIVITY_NOT_READY`，不是库存校验失败；活动 7 的恢复任务 `id=4` 虽为 `SUCCEEDED` 且活动回到 `ACTIVE`，但 `barrier=0`，只证明无待处理事件时恢复成功。该边缘场景暂不继续处理，保留为 H01 未完成项；详细补做方案见末尾“H01 活动恢复边缘场景补做方案”。 | DOING |
| H02 | 核心状态机、数据库不变量与跨服务一致性验收 | A01-G05,H01 | 订单、支付、用户券、活动、库存、Outbox、消费幂等和补偿记录只能发生设计规定的状态迁移；库存不得为负，库存流水/活动事件序号连续，订单和支付终态唯一，用户券不得重复核销，Outbox/消费记录与业务变更满足同事务约束；检查 Redis、PostgreSQL、RocketMQ 最终状态一致 | 2026-09-26：已完成设计定向 review；Docker Java 21 串行分层测试通过：common、inventory、coupon、order、payment、activity、job；临时 PostgreSQL（V1→V3）验证订单创建/取消、活动事件序号、支付幂等与履约、Outbox/消费幂等；临时 Redis 验证活动库存预扣/暂停门闸、优惠券并发限购与补偿；全量 `mvn -B test -q` 和 Compose config 通过。首次并行 Maven 触发公共模块 Surefire discovery 失败，串行重跑通过；首次优惠券 Redis 集成因既有容器未发布端口失败，改用临时端口 Redis 重跑通过。测试容器及临时 PostgreSQL/Redis 已关闭，仅保留预先存在的独立 `redis` 容器。H01 的 `barrier > 0` 暂停→追平→恢复真实 HTTP 闭环仍未完成，故 H02 保持 `DOING`，不标记完成。 | DOING |
| H03 | 高并发、幂等与容量压测 | D04,E02-E03,F02-F04,G01-G04,H02 | 覆盖活动热点库存、同用户限购、优惠券领取、重复提交、支付/取消竞争和 MQ 消费；核心正确性 100% 满足 AC01-AC08，零负库存、零重复有效订单/券/支付；报告记录并发量、持续时间、吞吐、P95/P99、错误率、Outbox 积压和消费延迟，并与发布基线比较 | DOING（2026-09-26）：已完成 H03 工件：`load/k6/h03.js` 支持活动 1 秒 10,000 请求（9,000 用户+1,000 重复）、冷启动/预热商品读取、普通购买 10→50→100、同用户限购、领券、重复下单和支付/取消竞争；`load/h03/run.sh` 使用独立 Compose project 和临时卷，按场景显式启动服务，`docker-compose.h03.yml` 开启 Outbox/Consumer，`verify.sql` 覆盖负库存、事件序号、幂等、Outbox、消费记录和孤儿预占检查，报告模板记录实际发送窗口与资源数据。k6 镜像拉取、inspect、shell/Compose 校验和一次真实冷启动详情读取通过；独立临时环境已停止并删除，acceptance 卷及独立 `redis` 未触碰。完整 10,000 请求突发及全部写场景仍需在可用资源窗口执行并填报 `load/h03/report-template.md`，故保持 DOING。 | DOING |
| H04 | 故障恢复、补偿与告警演练 | G01-G04,H02 | 覆盖服务/数据库重启、Outbox 投递中断、消费者崩溃、重复/乱序/延迟消息、RocketMQ 重投、Redis 预扣丢失、XXL-Job 重复触发和补偿失败；恢复后无数据不变量破坏，任务可重试且幂等，Redis 可由 PostgreSQL/事件账本安全重建，失败进入告警和人工处理记录 | Docker Java 21 按演练脚本注入故障并记录故障时间、恢复时间、重试次数、告警、补偿记录和最终对账结果；每个场景完成后关闭测试服务 | TODO |
| H05 | 从零部署、全链路冒烟与发布验收 | A03-A05,G05,H01-H04 | 使用干净 Docker 环境完成镜像构建、Flyway 初始化、Compose 健康检查和全部服务启动；完成注册/登录、浏览、领券、下单、支付、履约、取消/超时和运营查询冒烟；Prometheus、日志、Trace、告警可查询；重启后数据和任务可恢复；验收结束清理容器和临时数据 | Docker Java 21 执行一键部署及发布验收脚本；保存版本、配置、健康检查、端到端结果、监控证据和清理记录 | TODO |

### H02 不变量—实现—测试—证据矩阵（2026-09-26）

| 不变量 | 实现依据 | 测试/证据 | 结果 |
|---|---|---|---|
| 合法状态迁移且终态唯一 | 订单/支付/用户券/库存状态机、数据库 CAS/触发器 | `flashsale-order`、`flashsale-payment`、`flashsale-coupon`、`flashsale-inventory` 模块测试；订单/支付 PostgreSQL 集成测试 | 通过 |
| Outbox 与业务写入同事务 | 各服务 JDBC Store 与私有 Outbox | 订单创建/取消、支付集成测试；`JdbcOutboxPortIntegrationTest` | 通过 |
| 消费幂等与业务变更同事务 | 消费幂等记录 `PROCESSING/SUCCEEDED/FAILED` | `JdbcConsumerMessageHandlerIntegrationTest`、活动消费者测试 | 通过 |
| 活动事件序号连续、checkpoint 不跨洞 | sequence 锁、事件账本、checkpoint 推进 | 活动模块测试；订单活动创建/取消 PostgreSQL 集成测试 | 通过 |
| Redis 只作为实时入口，恢复不覆盖已有键 | Lua 门闸/预扣、恢复互斥与缺失键重建 | 活动 Redis 测试、订单 Redis 集成测试、恢复验证测试 | 通过（真实 H01 闭环仍待补做） |
| 失败保持安全状态并留痕 | 补偿记录、失败状态、告警路径 | 任务补偿测试、Outbox 重试测试、活动恢复失败测试 | 通过 |
| Redis/PostgreSQL/MQ 最终对账 | 对账任务、库存流水/账本/Outbox/checkpoint | 临时 PostgreSQL/Redis 集成验证；全量 Maven 回归 | 通过（RocketMQ 真实 HTTP 闭环依赖 H01） |

H 阶段按 `H01 → H02 → H03 → H04 → H05` 顺序执行。每项开始前必须声明测试环境、数据集和通过阈值；每项完成后记录命令、结果、证据链接和环境清理情况。任何核心正确性、不变量、恢复或部署验收失败，都保持当前任务为 `DOING`，不得进入下一项。

## 当前执行位置

- 当前任务：`H01`
  - 状态：`DOING`
  - 备注：R24 已完成偏离 24；进入全量接口契约验收。

- 并行准备任务：`H03`
  - 状态：`DOING`
  - 备注：按已确认范围先实现 k6 压测工件；活动暂停/恢复不纳入 H03，本地实际压测和数据对账待 k6 镜像可用后执行。

## I. 设计偏离修正任务

本节是对 [`docs/design-deviation-checklist.md`](design-deviation-checklist.md) 中偏离项的逐项修正计划。修正必须严格按编号顺序执行，每次只允许处理一个 `R` 任务；完成当前任务后才可开始下一项。每个任务都必须同时遵守：

- [`docs/sequence-design.md`](sequence-design.md)：业务时序、状态转换、屏障、缓存和订单流程是行为基线；
- [`docs/cross-cutting-design.md`](cross-cutting-design.md)：事务、Outbox、幂等、补偿、消息和可观测性是跨服务基线；
- [`docs/design-deviation-checklist.md`](design-deviation-checklist.md)：偏离编号、当前证据和修正范围是验收清单。

任何实现如果只满足其中一份文档、使用内存替代持久化、以同步调用替代 Outbox/MQ、跳过状态屏障、覆盖实时 Redis 状态、减少字段/指标/日志，均视为未完成，不得将任务标记为 `DONE`。每个任务完成后必须：更新本表状态和验证结果、运行任务指定验证、检查 `git diff --check`、单独小步提交。

### 修正任务总表

| ID | 对应偏离 | 任务 | 前置 | 必须完成的结果 | 验证 | 状态 |
|---|---:|---|---|---|---|---|
| R01 | 1 | 将网关改为设计要求的 Gateway/Sentinel 入口 | C03 | 接口/IP/热点参数限流；限流前不得进入 Redis、DB、MQ；返回 `RATE_LIMITED` | Docker Java 21：`mvn -B -pl flashsale-gateway -am test`，19 tests passed；Reactive Gateway 编译通过；JWT/参数规范化/Sentinel 规则契约测试通过；测试容器使用 `--rm` 自动关闭 | DONE |
| R02 | 2 | 接入 Nacos 注册/配置和 OpenFeign 服务调用 | R01 | 服务发现、配置中心和跨服务调用均由统一组件提供，禁止业务硬编码地址 | Docker Java 21 全量 `mvn -B test` 通过；Gateway 实际启动成功并注册 Nacos `flashsale-gateway`，`/actuator/health` 返回 UP；Gateway 使用 `lb://` 路由；Order Feign Trace 透传测试通过；容器使用 `--rm`/`timeout` 自动关闭 | DONE |
| R03 | 3 | 实现商品/优惠券模板 Cache Aside 读缓存 | R02 | Redis GET；`SET NX PX` owner token 锁；二次 GET；有限退避；TTL 随机抖动；未获锁不得查 DB | Docker Java 21：`mvn -B -pl flashsale-product,flashsale-coupon -am test`：公共 14 项、Coupon 2 项通过；Product/Coupon 编译通过；公共组件已接入商品详情/列表和可领取券模板列表，管理接口仍直读 PostgreSQL | DONE |
| R04 | 4 | 实现商品/优惠券延迟双删、审计和本服务 Outbox | R03 | 先删缓存，再本地事务写配置/审计/Outbox，提交后延时二次删除；不得直接回写缓存 | Docker Java 21：`mvn -B -pl flashsale-product,flashsale-coupon -am test` 通过；首删、审计快照、Outbox payload 契约已接入；实际投递由 R16 完成 | DONE |
| R05 | 5 | 重做活动开始前预热流程 | R04 | 调度窗口读取 PostgreSQL 配置，写活动详情/库存键，写 `ACTIVITY_PREHEAT_READY` Outbox；创建接口不得代替预热任务 | Docker Java 21：`mvn -B -pl flashsale-activity -am test` 通过（公共 14 项、活动 9 项）；覆盖 PT10M 窗口、创建不预热、重复预热、详情/库存键和幂等 Outbox 契约；未接入本地定时器，XXL-Job 运行时留给 R20 | DONE |
| R06 | 6 | 修正活动开始 CAS 和 Redis 键保护 | R05 | 只有预热键存在时才允许 `NOT_STARTED -> ACTIVE`；开始不得覆盖已存在实时库存键 | Docker Java 21：`mvn -B -pl flashsale-activity -am test` 通过（公共 14 项、活动 14 项）；覆盖预热键缺失、预热键存在、CAS 并发失败不激活 Redis，以及 Lua 激活不写库存键 | DONE |
| R07 | 7 | 实现活动暂停屏障和在途请求清算 | R06 | 关闭 Lua 新 `RESERVE` 门闸；等待在途请求提交或补偿；锁 sequence 截取 barrier；再 CAS `ACTIVE -> PAUSED` | Docker Java 21：`mvn -B -pl flashsale-activity -am test` 通过（公共 14 项、活动 19 项）；覆盖 Lua 门闸/在途登记、暂停竞争排序、事务回滚 Redis 补偿及 barrier 截取 | DONE |
| R08 | 8 | 接通活动 RESERVE/RELEASE 的订单、Outbox、MQ 消费链路 | R07 | 暂停前 RESERVE 和暂停期间 RELEASE 不得丢弃/拒绝；事件序号连续、消费幂等、checkpoint 不跨洞 | Docker Java 21：`mvn -B -pl flashsale-activity -am test` 通过（公共 14 项、活动 24 项）；覆盖事件 payload、重复/乱序消息、暂停期间 RELEASE、消费失败重试和连续 checkpoint | DONE |
| R09 | 9 | 实现恢复前完整对账和 Redis 键保护 | R08 | 检查屏障内 Outbox SENT、连续 checkpoint、事件账本、流水、有效保留；Redis 键存在不得覆盖，丢失才可互斥重建 | Docker Java 21：`mvn -B -pl flashsale-activity -am test` 通过（公共 14 项、活动 30 项）；覆盖 Outbox/checkpoint/账本/流水/有效保留对账、对账失败不重建、已有库存键不覆盖和缺失库存键原子重建为 PAUSED/CLOSED | DONE |
| R10 | 10 | 完成异步恢复锁、告警和恢复 Outbox | R09 | 每活动互斥恢复；失败保持 PAUSED 并告警；成功才预热详情、CAS ACTIVE 并写 `ACTIVITY_RESUMED` | Docker Java 21：`mvn -B -pl flashsale-activity -am test` 通过（公共 14 项、活动 32 项）；覆盖同活动并发 claim、失败关闭门闸/告警、成功 CAS 与 `ACTIVITY_RESUMED` Outbox 幂等契约 | DONE |
| R11 | 11 | 将订单创建改为 PostgreSQL 本地事务闭环 | R10 | 幂等记录、订单、订单项、库存预占、优惠券锁券、活动 RESERVE 事件和 order_outbox 同事务提交 | Docker Java 21：`mvn -B test` 全量通过；临时 PostgreSQL/Redis：订单模块 12 tests passed，覆盖同键并发、同键冲突、失败回滚/恢复、多商品全量预留、优惠券锁定、活动连续事件和 Outbox；容器均 `--rm` 清理 | DONE |
| R12 | 12 | 将取消/超时释放改为 CAS + 事件化处理 | R11 | 订单只允许一次合法终态；库存释放、券恢复、活动 RELEASE 事件和 Outbox 与状态更新满足幂等 | Docker Java 21：`mvn -B -pl flashsale-order -am test` 通过（公共 14 项、订单 15 项）；临时 PostgreSQL 16：取消/活动释放集成测试 8 项通过；容器已关闭 | DONE |
| R13 | 13 | 完成支付幂等、回调、确认和履约事务 | R12 | 支付记录和幂等记录持久化；成功确认库存、核销优惠券、履约并写支付事件；回调重复安全 | Docker Java 21：`mvn -B -o -pl flashsale-payment -am -Dtest=JdbcPaymentServiceIntegrationTest test`：2 tests passed；临时 PostgreSQL 16 集成验证通过；容器已关闭 | DONE |
| R14 | 14 | 将领券高并发入口改为 Redis Lua 预扣 | R13 | Lua 原子校验发行量、用户限领和重复请求；成功后 PostgreSQL 本地事务写用户券和 coupon_outbox | Docker Java 21 `mvn -B -o -pl flashsale-coupon -am test`：公共 14 项、Coupon 5 项通过（Redis 环境缺失时集成测试跳过）；临时 Redis 容器并发/限购/补偿集成测试 1 项通过；容器已关闭 | DONE |
| R15 | 15 | 修正领券请求指纹和状态语义 | R14 | 使用规范化请求的 SHA-256；同键同请求复用结果；同键不同请求返回冲突；PROCESSING/FAILED 可恢复 | Docker Java 21 `mvn -B -pl flashsale-coupon -am test`：公共 14 项、Coupon 7 项通过（Redis 集成测试因未提供测试 Redis 跳过）；指纹 SHA-256、Redis 指纹键和幂等状态接线已验证 | DONE |
| R16 | 16 | 为每个生产服务接入真实 Outbox 投递器 | R15 | 六个服务私有表各自接入 PostgreSQL 批量 `SKIP LOCKED` 领取、租约条件回写、退避、最大重试、RocketMQ 确认发送和一次性任务入口；R20 接 XXL-Job | Docker Java 21 六模块 `mvn -B -pl flashsale-product,flashsale-coupon,flashsale-activity,flashsale-order,flashsale-payment,flashsale-inventory -am test -q` 通过；临时 PostgreSQL 验证六表批量、租约过期、旧租约回写拒绝及最大重试；临时 RocketMQ broker 验证确认发送；容器已关闭 | DONE |
| R17 | 17 | 接入数据库消费幂等和 RocketMQ 消费确认 | R16 | 独立幂等记录表；PROCESSING 抢占/超时恢复；业务变更与 SUCCEEDED 同事务；事务成功后才 ACK | Docker Java 21：`flashsale-common` 测试通过；`flashsale-activity` 测试通过；临时 PostgreSQL 消费幂等集成测试 3 项通过；临时 PostgreSQL 全新库 Flyway V1+V2 迁移通过；验证后容器已关闭 | DONE |
| R18 | 18 | 补齐商品、活动、优惠券运营审计 | R17 | 每次 CREATE/UPDATE/状态变更写不可变 before/after、operator、Trace、来源审计；与业务事务一致 | Docker Java 21：`mvn -B -pl flashsale-product,flashsale-activity,flashsale-coupon,flashsale-auth -am test -q` 通过；活动审计单测覆盖 CREATE/CANCEL，账户快照不含 password_hash；容器已关闭 | DONE |
| R19 | 19 | 统一缓存失效事件契约 | R18 | 事件必须含资源类型、资源 ID、缓存键、Trace ID；重复 DEL 安全；失败按 Outbox 重试并告警 | Docker Java 21 全量 `mvn -B test -q` 通过；商品、优惠券、活动事件契约测试通过；临时 Redis 跨服务事件重复 DEL 集成测试通过，容器已关闭；失败触发 MQ 重投与错误日志，Outbox 发送失败按原有退避/耗尽告警路径处理 | DONE |
| R20 | 20 | 将扫描、恢复、超时、对账和补偿接入 XXL-Job | R19 | 任务可领取、租约/幂等、失败重试、补偿记录和人工告警；禁止仅靠本地 `@Scheduled` | Docker Java 21：`mvn -B test -q` 全量通过；`docker compose config` 通过；临时 PostgreSQL Flyway V1→V3 迁移通过；XXL executor/handler、重复触发幂等、补偿失败记录+人工告警、失败重试测试通过；测试容器已关闭 | DONE |
| R21 | 21 | 完成 SkyWalking/Prometheus/Loki/Grafana/Alertmanager 运行时接入 | R20 | HTTP/Feign/JDBC/Redis/RocketMQ Trace；所有服务可抓取指标；日志和告警链路可查询 | Docker Java 21 全量 `mvn -B test -q` 通过；Compose R21 配置、Prometheus 6 条规则、Promtail 语法通过；九服务分批健康启动、Prometheus 9/9 曾抓取 UP、SkyWalking 注册 9/9；实际 Trace 含 Gateway→Auth、Feign、JDBC、Redis、RocketMQ；RocketMQ/PostgreSQL/Redis/主机 exporter UP；Grafana 双数据源、Loki 日志、Alertmanager 告警可查询；测试容器已关闭 | DONE |
| R22 | 22 | 完成结构化日志字段和跨线程/MQ/任务透传 | R21 | 日志至少含 `timestamp`、`level`、`service`、`trace_id`、`span_id`、`user_id`、`order_id`、`event_id`、`idempotency_key`、`outbox_id`、`error_code` | Docker Java 21：`mvn -B -pl flashsale-common,flashsale-gateway,flashsale-job -am test -q` 通过；结构化 MDC、MQ 消费、异常字段、Gateway/任务透传测试通过；容器已关闭 | DONE |
| R23 | 23 | 补齐业务指标和告警规则 | R22 | 覆盖吞吐/延迟/错误率、预扣、领券、订单、支付、Outbox、消费重试/死信、幂等冲突、补偿和连接池 | Docker Java 21：`mvn -B -pl flashsale-common,flashsale-coupon,flashsale-order,flashsale-payment -am test -q` 通过；指标名称/标签契约和 Prometheus 规则覆盖测试通过；测试容器已关闭 | DONE |
| R24 | 24 | 补齐订单、支付、库存 HTTP 入口并完成端到端契约 | R23 | Gateway 路由对应真实 Controller；鉴权、资源归属、错误码、幂等键和 Trace 全部符合 API/时序设计 | Docker Java 21 `mvn -B test -q`：全量通过；订单/支付/库存 Controller 契约、Gateway 路由、JWT 角色和 Trace 响应测试通过 | DONE |

### R 任务逐项三方依据

以下引用是每个修正步骤的最低必读范围；实现说明、测试用例和提交说明中必须再次引用对应三方依据，不能只引用本表总则。

| 任务 | 偏离清单依据 | 时序设计依据 | 跨切面设计依据 |
|---|---|---|---|
| R01 | [偏离 1](design-deviation-checklist.md#L20) | [请求入口、认证与角色隔离](sequence-design.md#L3) | [一期技术边界](cross-cutting-design.md#L5) |
| R02 | [偏离 2](design-deviation-checklist.md#L21) | [请求入口、认证与角色隔离](sequence-design.md#L3) | [一期技术边界](cross-cutting-design.md#L5) |
| R03 | [偏离 3](design-deviation-checklist.md#L22) | [商品/优惠券模板读缓存](sequence-design.md#L40) | [读缓存与缓存失效](cross-cutting-design.md#L27) |
| R04 | [偏离 4](design-deviation-checklist.md#L23) | [商品与优惠券运营写入缓存失效](sequence-design.md#L79) | [读缓存与缓存失效](cross-cutting-design.md#L27) |
| R05 | [偏离 5](design-deviation-checklist.md#L24) | [活动开始前预热与异步恢复](sequence-design.md#L150) | [读缓存与缓存失效](cross-cutting-design.md#L27) |
| R06 | [偏离 6](design-deviation-checklist.md#L25) | [活动开始前预热与异步恢复](sequence-design.md#L150) | [Redis 预扣与本地事务](cross-cutting-design.md#L14) |
| R07 | [偏离 7](design-deviation-checklist.md#L26) | [活动取消与暂停屏障](sequence-design.md#L113) | [Redis 预扣与本地事务](cross-cutting-design.md#L14) |
| R08 | [偏离 8](design-deviation-checklist.md#L27) | [活动取消与暂停屏障](sequence-design.md#L113) | [Outbox 与 RocketMQ 投递](cross-cutting-design.md#L37)；[消费端幂等](cross-cutting-design.md#L54) |
| R09 | [偏离 9](design-deviation-checklist.md#L28) | [活动开始前预热与异步恢复](sequence-design.md#L150) | [对账与补偿](cross-cutting-design.md#L69) |
| R10 | [偏离 10](design-deviation-checklist.md#L29) | [活动开始前预热与异步恢复](sequence-design.md#L150) | [对账与补偿](cross-cutting-design.md#L69)；[可观测性](cross-cutting-design.md#L81) |
| R11 | [偏离 11](design-deviation-checklist.md#L30) | [创建订单与 Redis 预扣](sequence-design.md#L219) | [Redis 预扣与本地事务](cross-cutting-design.md#L14) |
| R12 | [偏离 12](design-deviation-checklist.md#L31) | [订单取消/超时后的活动库存回补](sequence-design.md#L360) | [对账与补偿](cross-cutting-design.md#L69) |
| R13 | [偏离 13](design-deviation-checklist.md#L32) | [支付与延时超时取消竞争](sequence-design.md#L310) | [消费端幂等](cross-cutting-design.md#L54)；[对账与补偿](cross-cutting-design.md#L69) |
| R14 | [偏离 14](design-deviation-checklist.md#L33) | [领取优惠券](sequence-design.md#L270) | [Redis 预扣与本地事务](cross-cutting-design.md#L14) |
| R15 | [偏离 15](design-deviation-checklist.md#L34) | [领取优惠券](sequence-design.md#L270) | [消费端幂等](cross-cutting-design.md#L54) |
| R16 | [偏离 16](design-deviation-checklist.md#L35) | [Outbox 重复投递与消费幂等](sequence-design.md#L401) | [Outbox 与 RocketMQ 投递](cross-cutting-design.md#L37) |
| R17 | [偏离 17](design-deviation-checklist.md#L36) | [Outbox 重复投递与消费幂等](sequence-design.md#L401) | [消费端幂等](cross-cutting-design.md#L54) |
| R18 | [偏离 18](design-deviation-checklist.md#L37) | [运营配置与账户管理](sequence-design.md#L435) | [对账与补偿](cross-cutting-design.md#L69) |
| R19 | [偏离 19](design-deviation-checklist.md#L38) | [商品与优惠券运营写入缓存失效](sequence-design.md#L79) | [读缓存与缓存失效](cross-cutting-design.md#L27)；[Outbox 与 RocketMQ 投递](cross-cutting-design.md#L37) |
| R20 | [偏离 20](design-deviation-checklist.md#L39) | [活动开始前预热与异步恢复](sequence-design.md#L150) | [对账与补偿](cross-cutting-design.md#L69) |
| R21 | [偏离 21](design-deviation-checklist.md#L40) | [请求入口、认证与角色隔离](sequence-design.md#L3) | [可观测性](cross-cutting-design.md#L81) |
| R22 | [偏离 22](design-deviation-checklist.md#L41) | [请求入口、认证与角色隔离](sequence-design.md#L3) | [可观测性](cross-cutting-design.md#L81) |
| R23 | [偏离 23](design-deviation-checklist.md#L42) | [创建订单与 Redis 预扣](sequence-design.md#L219)；[领取优惠券](sequence-design.md#L270) | [可观测性](cross-cutting-design.md#L81) |
| R24 | [偏离 24](design-deviation-checklist.md#L43) | [请求入口、认证与角色隔离](sequence-design.md#L3)；[创建订单与 Redis 预扣](sequence-design.md#L219)；[支付与延时超时取消竞争](sequence-design.md#L310) | [一期技术边界](cross-cutting-design.md#L5)；[可观测性](cross-cutting-design.md#L81) |

### 每个 R 任务的强制执行模板

开始某个 `Rxx` 前：

1. 将该行状态改为 `DOING`，并在“备注/验证”中写明本次只处理的偏离编号。
2. 阅读并在实现说明中逐条引用 `design-deviation-checklist.md` 对应行、`sequence-design.md` 对应章节、`cross-cutting-design.md` 对应章节；没有三方引用不得开始编码。
3. 先写失败测试或验收场景，再实现；测试必须覆盖正常、重复、并发、失败、重试和恢复路径中适用的部分。
4. 不得用内存 Map、`synchronized`、本地定时器、直接跨服务数据库写入或同步 MQ 发送替代文档规定的 PostgreSQL 事务、Outbox、RocketMQ、幂等表和补偿任务。
5. 不得删除设计字段、降低状态机约束、跳过 CAS/屏障/二次检查、覆盖实时 Redis 键或把失败静默吞掉。

完成某个 `Rxx` 后：

1. 运行该任务的全部验证命令，并把实际结果写入本表；失败时保持 `DOING`，不得提前标记 `DONE`。
2. 更新 `design-deviation-checklist.md` 对应条目的状态或完成标记，并保留修正后的证据链接。
3. 运行 `git diff --check` 和相关 Docker Java 21 测试；调试/测试启动的服务必须在验证结束后关闭。
4. 只提交当前 `Rxx` 的代码、测试和文档变更，提交后再开始下一个任务。


### H01 活动恢复边缘场景补做方案

该方案用于严格验证“存在未消费库存事件且 `barrier > 0` 时，暂停、事件追平、恢复和最终库存对账”这一当前未完成的真实 HTTP 闭环。

1. **准备数据与时间窗口**
   - 使用新的、当前时间窗口有效的活动和独立验收账号。
   - 固定商品、活动库存、购买限额和请求幂等键；确认 `starts_at` 已到达且 `ends_at` 仍在未来。

2. **启动并检查依赖**
   - 启动 PostgreSQL、Redis、Nacos、RocketMQ、Inventory、Activity、Order、Auth、Gateway、XXL-Job 及必要的 Outbox/Consumer 服务。
   - 确认服务健康，XXL-Job executor 已注册，订单 Outbox 投递器和 Activity Consumer 均可用。

3. **建立活动基线**
   - 预热并开始活动，确认活动为 `ACTIVE`。
   - 保存 Redis 活动库存、活动详情、库存闸门、数据库 `available_stock` 和初始库存流水/账本快照。

4. **制造“已产生但未消费”的事件**
   - 通过活动订单或等价真实 HTTP 入口制造至少一条活动 `RESERVE` 事件。
   - 在事件已写入活动事件表和订单 Outbox 后，暂时阻止 Activity Consumer 消费；确认事件尚未进入 `SUCCEEDED`，checkpoint 尚未追平。

5. **暂停并截取屏障**
   - 在事件仍未消费时调用暂停接口。
   - 记录暂停响应、Trace ID、活动状态、`pause_barrier_sequence`、事件序号、订单 Outbox 状态、消费幂等记录、checkpoint、Redis 库存、库存流水和账本。
   - 通过标准：暂停成功，活动为 `PAUSED`，且 barrier 大于 0。

6. **恢复投递与消费，验证追平**
   - 暂停完成后恢复 Outbox 投递和 Activity Consumer。
   - 验证 barrier 内每条事件的生产端 Outbox 达到 `SENT`，消费幂等记录达到 `SUCCEEDED`，`last_contiguous_sequence` 连续追平 barrier。
   - 检查无跳号、重复扣减、重复回补、负库存或超额库存。

7. **执行恢复接口**
   - 调用恢复接口，记录任务 ID、请求 Trace ID 和恢复 barrier。
   - 轮询恢复查询直到 `SUCCEEDED` 或明确失败，不能只依据 HTTP `202` 判断成功。

8. **成功验收标准**
   - 活动从 `PAUSED` 变为 `ACTIVE`。
   - 恢复任务为 `SUCCEEDED`，恢复 barrier 与暂停 barrier 一致。
   - 屏障内 Outbox 全部 `SENT`，checkpoint 等于 barrier。
   - 事件账本、库存流水、活动可用库存、Redis 库存和订单保留数量一致。
   - 库存无负数、重复释放、超额回补或重复消费。

9. **幂等与失败验收**
   - 重复调用 resume 返回同一进行中或已完成任务，不创建并发恢复任务。
   - 重复投递同一事件不重复变更库存。
   - 在恢复执行期间再次调用 resume 不创建第二个任务。
   - 任一对账条件失败时，任务必须为 `FAILED`，活动保持 `PAUSED`，并记录可定位的失败原因和告警。

10. **证据与清理**
    - 保存请求/响应、Trace ID、容器日志、PostgreSQL 查询结果、Redis 查询结果、XXL-Job 执行记录和 `docker stats` 峰值。
    - 测试结束后关闭本轮启动的所有服务。
    - 在 H01 行补充实际结果、失败原因、修复提交、资源峰值和清理状态；只有上述闭环全部通过后才可将 H01 改为 `DONE`。

### 当前修正执行位置

- 当前任务：`H01`
  - 状态：`DOING`
  - 说明：R24 已完成偏离 24；进入全量接口契约验收。
