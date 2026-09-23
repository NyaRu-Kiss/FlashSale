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
| C02 | ADMIN 账户管理 | C01 | 角色、启停用、最后 ADMIN 保护和审计完整 | Docker Java 21 `mvn -B test`：通过 | DONE |
| C03 | Gateway 路由、鉴权与限流 | B02-B03,A03 | 路由、JWT、Sentinel、Trace 生效 | Docker Java 21 `mvn -B test`：通过 | DONE |

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
| E01 | 活动创建、预热、开始、结束 | B01-B05,C01 | 活动基础状态和缓存正确 | 活动状态机测试 | DONE |
| E02 | 活动库存事件序号与账本 | E01,B05 | RESERVE/RELEASE 连续序号、Outbox 同事务提交 | 事件账本测试 | DONE |
| E03 | 活动库存消费、流水和 checkpoint | E02,B06 | 重复消息不重复变更，checkpoint 不跨洞 | checkpoint 测试 | DONE |
| E04 | 活动 PAUSING/暂停屏障 | E02-E03 | 关闭新预扣、清算在途、建立 pause barrier；崩溃可恢复 | 暂停屏障测试 | DONE |
| E05 | 异步恢复任务与 Redis 校验重建 | E04,B05-B06 | Outbox、checkpoint、账本、Redis 校验后才 ACTIVE；不一致可重建 | 恢复判定测试 | DONE |
| E06 | 活动查询、指标和运营接口 | E01-E05 | 对外查询不泄露内部处理中状态，指标可追踪 | 查询与指标测试 | DONE |

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
| H01 | 全量接口契约测试 | C03,D01,D03,E06,F01,F05 | API 与文档字段、错误码、权限一致 | 自动化测试 | DOING |
| H02 | 核心状态机与数据库不变量测试 | 全部业务任务 | 无非法状态迁移、无负库存、无重复流水 | 自动化测试 | TODO |
| H03 | 高并发压测 | F02-F04,D04 | 热点库存、限购、幂等结果正确 | 压测报告 | TODO |
| H04 | 故障恢复演练 | G01-G03 | 崩溃、重复消息、Redis 丢失后可恢复 | 演练报告 | TODO |
| H05 | 发布前全量验收 | H01-H04 | Docker 环境从零部署并通过验收标准 | 验收记录 | TODO |

## 当前执行位置

- 当前任务：`H01`
  - 状态：`DOING`
  - 备注：已开始建立隔离的真实 HTTP 验收环境；G01-G05 已完成；Compose 已补齐 RocketMQ topic 初始化、独立 XXL-Job MySQL、Prometheus/Grafana/Alertmanager/Loki、健康检查和 Flyway 启动链路。Java 21 测试与基础设施启动验证已通过；MinIO 使用可访问的 Quay 镜像源，XXL-Job 默认端口调整为 18088 以避开本机 8088 端口冲突。
