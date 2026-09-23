# 一期实现缺口审计

> 审计范围：当前 `main` 分支的静态代码、`docs/task.md`、`docs/api-design.md` 和 Compose 配置。
>
> 本文记录的是“代码可验证的实现缺口”，不代表所有领域逻辑均不存在，也不替代真实 HTTP、数据库、Redis、RocketMQ 故障验收。

## 结论

任务清单中 A–G 已全部标记为 `DONE`，但当前工程尚未形成满足接口设计的一期可部署系统。已有部分领域对象、状态机和单元测试；缺失主要集中在运行时装配、持久化、跨服务协作和端到端验收。

完整补齐至任务清单的完成标准，预计约 **44–66 人日**；若仅交付真实 HTTP + PostgreSQL 的演示 MVP，且暂不包含 MQ 故障恢复、活动恢复、完整对账与监控告警，预计约 **15–25 人日**。

## 接口、鉴权与网关

| 对应任务 | 当前证据 | 缺口/不符合项 | 影响 |
|---|---|---|---|
| C03 | 仅 Auth、Product、Coupon 有 Controller；Activity、Inventory、Order、Payment、Job 没有业务 Controller | 缺少活动、订单、支付、库存等对外和运营端 API；文档中 38 组接口无法完成契约验收 | 高 |
| C03 | `flashsale-gateway` 只有 Servlet 过滤器和 CORS | 没有路由代理、服务发现、负载均衡或 Sentinel；限流为单 JVM 内存计数 | 高 |
| B03/C03 | Gateway 对 `/api/v1/admin/**` 允许 ADMIN 进行运营操作 | 与 API 设计中 ADMIN 不可管理商品、活动、优惠券的角色隔离冲突 | 高 |
| B03/D01/D03 | Product 详情直接返回任意状态；Coupon 后台查询只解析 JWT | 商品可见性和 OPERATOR 权限校验不完整 | 高 |
| C02 | 已有账户创建、启停、角色变更 | 缺少 `/admin/users/audit-logs`；没有部署期受控初始 ADMIN 创建方式 | 中 |
| D01/D03 | 已有部分商品和优惠券模板接口 | 缺少后台商品列表/详情/库存、优惠券指标、我的优惠券等设计接口 | 中 |

## 领域业务与持久化

| 对应任务 | 当前证据 | 缺口/不符合项 | 影响 |
|---|---|---|---|
| D02 | `InventoryLedger` 使用 `ConcurrentHashMap` | 无 PostgreSQL 库存预留、条件扣减、库存流水或可恢复的幂等记录 | 高 |
| D04-D05 | 优惠券领取直接操作数据库 | 未使用 Redis Lua 原子预扣；未接入订单锁券、核销、取消/超时恢复及用户券完整状态机 | 高 |
| E01-E06 | Activity 只有内存状态机和账本对象 | 无活动 Repository、Redis 预热/预扣、暂停屏障、异步恢复、指标或运营接口；状态机也未实现 pause/resume | 高 |
| F02-F04 | Order 是内存 Repository 与接口网关 | 无订单/订单项/提交幂等表持久化；Product、Activity、Coupon、Inventory 网关均无生产实现；取消未联动活动库存和优惠券 | 高 |
| F05-F07 | Payment 仅内存幂等集合 | 无支付记录、支付幂等持久化、回调落库、支付确认、履约或延时超时任务；成功支付直接写 `COMPLETED`，未经历 `PAID → 履约` | 高 |
| A04 | 数据库迁移已建立 33 张表 | 大部分业务表没有被生产代码引用，例如订单、库存预留/流水、支付、履约、活动账本和恢复任务表 | 高 |

## 消息、补偿与可观测性

| 对应任务 | 当前证据 | 缺口/不符合项 | 影响 |
|---|---|---|---|
| B05/G01 | 有 Outbox 接口、内存实现和通用 Dispatcher | 没有 JDBC Outbox、服务级投递器、定时调度或真实业务事务接线 | 高 |
| B06/G02 | 有消费者幂等接口、内存实现和 RocketMQ 适配类 | 没有数据库消费幂等实现、消费者订阅、失败重试或死信处理接线 | 高 |
| G03 | 有通用 `ReconciliationTask` 与内存补偿存储 | 没有实际对账规则、持久化补偿记录、XXL-Job 执行器注册或调度配置；`RecoveryJobHandlers` 依赖的 `ReconciliationTask` 也未注册为 Bean | 高 |
| G04 | 有 `MessagingMetrics` 和 Prometheus/Grafana 配置 | 指标没有注册到业务运行时，也没有在投递、消费、补偿流程中更新；Loki 未接收应用日志 | 中 |
| B02 | 有 `TraceIdFilter` 和异常处理类 | 公共包没有被各服务组件扫描或显式导入，Trace Filter/全局异常处理未可靠接入业务服务 | 中 |

## 配置、部署与测试

| 对应任务 | 当前证据 | 缺口/不符合项 | 影响 |
|---|---|---|---|
| A05 | 公共配置文件位于 `config/` | 服务模块没有 `src/main/resources` 配置；公共配置不会被 Spring Boot 自动加载 | 高 |
| G05 | `docker-compose.yml` 启动 PostgreSQL、Redis、RocketMQ、Nacos、XXL-Job、MinIO 和观测组件 | 未启动 Gateway、Auth、Product、Activity、Inventory、Coupon、Order、Payment、Job，不能称为应用一键启动 | 高 |
| H01-H05 | 测试主要覆盖纯 Java 领域对象 | Product、Gateway、Payment、Migration 没有测试；无 HTTP、PostgreSQL、Redis、RocketMQ、故障恢复或从零部署验收 | 高 |
| H01-H05 | 已建立独立的验收 Compose 配置草案 | 需先补齐业务服务后才能做真实 HTTP 验收；不能以领域单元测试替代 | 高 |

## 任务状态建议

以下任务建议从 `DONE` 回退为“未完成”或新增“仅领域原型”标记，待达到对应完成标准后再恢复：

- `C03`
- `D02`–`D05`
- `E01`–`E06`
- `F02`–`F07`
- `G01`–`G05`
- `H01`–`H05`

`A01`–`A04`、`B01` 的工程或基础结构可保留已完成结论；其余任务需要在真实运行环境中重新验证。

## 验证记录

- 静态核查确认：标准 Compose 未定义任何业务服务；Activity、Inventory、Order、Payment、Job 均无业务 Controller。
- Docker Java 21 全量 Maven 测试曾两次因 Maven Central 下载响应被截断而中止，失败点为依赖传输而不是已执行测试断言失败；已执行完成的 `flashsale-common` 测试均通过。
- 本文仅为静态审计记录，不将上述网络问题记为代码测试失败或验收通过。
