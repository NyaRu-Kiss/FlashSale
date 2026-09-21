# 跨服务可靠性与可观测性设计

本文记录一期系统跨业务模块的统一技术约束，适用于活动、商品、订单、库存、优惠券、支付和用户等服务。

## 1. 一期技术边界

- 全部业务服务使用 Java 实现，基于 Spring Boot/Spring Cloud。
- 一期使用 Docker Compose 部署，不引入 Kubernetes。
- 业务数据使用 PostgreSQL 16；Redis 用于高并发预扣、限购和缓存；RocketMQ 用于异步事件和延时消息。
- Nacos 负责服务注册与配置，Spring Cloud Gateway/Sentinel 负责路由、鉴权入口和限流，OpenFeign 用于同步服务调用。
- XXL-Job 负责后台扫描、超时处理、对账和补偿任务。
- MinIO 存储商品图片等对象；数据库只保存对象标识或访问地址。

## 2. Redis 预扣与本地事务

Redis 只承担高并发入口的原子预扣，不是业务最终账本。库存或领券请求按以下顺序处理：

1. 业务服务通过 Redis Lua 脚本原子执行库存扣减、限购校验及必要的重复请求校验。
2. Redis 预扣失败时，立即向客户端返回库存不足或系统异常。
3. Redis 预扣成功后，业务服务开启 PostgreSQL 本地事务。
4. 本地事务同时写入业务记录（例如 `user_coupon`、订单或库存预占记录）和本服务私有的 Outbox 记录，Outbox 初始状态为 `PENDING`。
5. PostgreSQL 提交成功后返回业务成功；MQ 发送不属于用户请求的同步主流程。
6. PostgreSQL 提交失败时，事务回滚；Redis 与数据库的短暂不一致由对账/补偿任务回补。可在明确安全的情况下立即回补，但不能把 Redis 当作最终状态来源。

PostgreSQL 是业务状态与审计记录的最终权威。所有预扣、释放、确认和补偿操作都必须留下可追踪的业务记录。

## 3. Outbox 与 RocketMQ 投递

### 3.1 表归属

每个生产服务维护自己的 Outbox 表和投递任务，只能由该服务写入自己的 Outbox。共享 PostgreSQL 实例不改变服务的领域写权限边界。

### 3.2 投递流程

后台投递任务周期性扫描本服务的 `PENDING` 记录，向 RocketMQ 发送事件：

- 发送成功后将记录更新为 `SENT`。
- 发送失败不更新为 `SENT`，保留待投递状态并在下一轮重试。
- 发送成功但服务在更新 Outbox 前宕机时，记录仍可能是 `PENDING`，因此同一事件可能被重复发送。
- 投递任务必须具备批量、锁定/租约、重试次数、退避和异常告警能力，避免多个实例无界重复扫描。

消息体必须携带全局事件标识、业务幂等号、事件类型、生产服务、业务主键、创建时间和 Trace ID。

## 4. 消费端幂等

MQ 消费端必须按“至少一次投递”设计，不能依赖 RocketMQ 只投递一次。

- 每个消费者服务维护独立的消费幂等记录表。
- 幂等号必须带业务前缀，例如 `COUPON_CLAIM_<UUID>`、`STOCK_RESERVE_<UUID>`、`ORDER_PAID_<UUID>`、`ORDER_CANCEL_<UUID>`。
- 幂等号在消费者所属业务域内建立唯一约束，并记录事件号、消息号、业务主键、Trace ID、处理时间和错误信息。
- 消费记录状态为 `PROCESSING`、`SUCCEEDED`、`FAILED`。
- 开始处理前以唯一键抢占 `PROCESSING`；已是 `SUCCEEDED` 的消息直接确认并跳过业务处理。
- 业务数据变更与幂等记录更新为 `SUCCEEDED` 必须在同一个 PostgreSQL 事务中提交。
- 业务失败时记录 `FAILED` 并抛出可重试错误；不可重试错误进入告警/死信处理流程。
- `PROCESSING` 超时后由恢复任务判断是否重新处理，防止消费者宕机留下永久占用。

消费确认必须发生在业务事务成功之后。任何重复消费、重复确认、重复释放和重复回补都必须通过状态条件或唯一约束保证只生效一次。

## 5. 对账与补偿

XXL-Job 定期执行以下检查：

- Redis 预扣记录与 PostgreSQL 业务记录是否匹配。
- 已提交但长时间未发送的 Outbox 是否需要重试或告警。
- RocketMQ 重复投递是否均被消费幂等记录拦截。
- 订单取消、支付成功、优惠券恢复和库存释放是否出现状态不一致。
- `PROCESSING` 超时记录是否需要恢复、重试或人工介入。

补偿任务必须使用 CAS/状态条件更新，具备幂等性，并记录补偿原因、原状态、目标状态、执行结果和 Trace ID。补偿失败进入告警和人工处理队列，不得静默丢弃。

## 6. 可观测性

整个系统统一建设指标、链路和日志能力：

| 能力 | 技术组件 | 主要职责 |
|---|---|---|
| 分布式链路 | SkyWalking Java Agent + OAP/UI | Gateway、OpenFeign、JDBC、Redis、RocketMQ 等调用链和 Trace 关联 |
| 应用与基础设施指标 | Prometheus | 服务、JVM、PostgreSQL、Redis、RocketMQ、Nginx、主机等指标采集 |
| 看板 | Grafana | 延迟、吞吐、错误率、库存、订单、消息积压和补偿结果展示 |
| 告警 | Alertmanager | 服务异常、消息积压、投递失败、消费失败、库存不一致和资源不足告警 |
| 日志 | Loki + Grafana | 结构化日志集中采集、检索和 Trace 关联 |

所有服务日志使用结构化格式，至少包含：`timestamp`、`level`、`service`、`trace_id`、`span_id`、`user_id`（可用时）、`order_id`、`event_id`、`idempotency_key`、`outbox_id`、`error_code`。

必须监控的核心指标包括：请求吞吐/延迟/错误率、Redis 预扣成功率、库存与领券失败数、订单创建与取消数、支付成功率、Outbox 待投递数量与最老消息年龄、RocketMQ 消费重试/死信数量、幂等冲突数、补偿成功/失败数及数据库连接池使用率。

## 7. 约束与排除项

- 不使用 Seata；跨服务一致性采用本地事务、Outbox、幂等消费和补偿对账实现。
- 不引入 Elasticsearch、服务网格或 Kubernetes，除非后续需求明确要求。
- Redis、RocketMQ 和 MinIO 都不是业务最终账本；关键状态必须可在 PostgreSQL 中审计和恢复。
