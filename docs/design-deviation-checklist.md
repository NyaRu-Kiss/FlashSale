# 设计偏离清单

本文根据 `docs/sequence-design.md` 与 `docs/cross-cutting-design.md`，对当前代码进行静态核对，记录已确认的实现偏离。

## 总体结论

当前实现已经具备部分领域模型、数据库表结构、活动库存事件模型和公共消息状态机，但关键运行链路仍有明显缺口，主要集中在：

- 网关技术栈和限流实现未达到设计要求；
- 商品、优惠券缓存及延迟双删未接入；
- 活动暂停/恢复缺少完整的在途请求清算、账本对账和 Redis 安全重建；
- 订单、支付、库存服务仍以领域类或内存实现为主，未形成数据库事务闭环；
- Outbox、RocketMQ、消费者幂等组件多数停留在公共模型或测试实现，尚未完成生产接线；
- 可观测性只有部分 Actuator、消息指标和容器配置，未形成完整运行时能力。

## 偏离清单

| # | 优先级 | 设计要求 | 当前实现 | 证据 |
|---|---|---|---|---|
| 1 | 高 | 使用 Spring Cloud Gateway + Sentinel，按接口、IP 和热点参数限流 | ~~实际使用 Spring MVC Controller 代理；限流是单机内存 IP 每秒 100 次，没有 Sentinel、接口维度或热点参数维度~~ **已完成（R01）**：Reactive Spring Cloud Gateway 路由通过 Sentinel 实施全局、接口、IP 和活动/优惠券真实 ID 热点参数限流；限流在上游转发前返回 `RATE_LIMITED`，并透传 Trace ID | [GatewayRoutes.java](../flashsale-gateway/src/main/java/com/flashsale/gateway/GatewayRoutes.java)；[GatewayAccessFilter.java](../flashsale-gateway/src/main/java/com/flashsale/gateway/GatewayAccessFilter.java)；[GatewaySentinelConfiguration.java](../flashsale-gateway/src/main/java/com/flashsale/gateway/GatewaySentinelConfiguration.java)；Docker Java 21 `mvn -B -pl flashsale-gateway -am test`：19 tests passed |
| 2 | 高 | 使用 Nacos 服务注册/配置和 OpenFeign 服务调用 | ~~Gateway 使用环境变量硬编码服务地址和 `RestClient`，未发现 Nacos/OpenFeign 运行时接入~~ **已完成（R02）**：各 Spring Boot 服务统一接入 Nacos Discovery/Config；Gateway 使用 `lb://` 服务名路由；Order 启用 OpenFeign、LoadBalancer 及 Trace/Authorization 透传基础设施；配置使用 `@RefreshScope` 支持刷新 | [GatewayRoutes.java](../flashsale-gateway/src/main/java/com/flashsale/gateway/GatewayRoutes.java)；[application.yml](../flashsale-gateway/src/main/resources/application.yml)；[FeignPropagationConfiguration.java](../flashsale-order/src/main/java/com/flashsale/order/FeignPropagationConfiguration.java)；Docker Java 21 全量 `mvn -B test` 通过；Gateway 实际注册 Nacos `flashsale-gateway` 且 actuator health 为 UP |
| 3 | 高 | 商品和优惠券查询采用 Cache Aside、按键锁、二次检查和 TTL 抖动 | **已完成（R03）**：公共商品详情/列表和可领取券模板列表使用 Redis Cache Aside；按键 `SET NX PX` owner token 锁、二次 GET、3×25ms 有限退避、Lua owner-token 解锁、5 分钟+0–60 秒抖动；空列表/负缓存 30 秒。运营接口和库存查询仍直读 PostgreSQL | [CacheAsideReader.java](../flashsale-common/src/main/java/com/flashsale/common/cache/CacheAsideReader.java)；[ProductService.java](../flashsale-product/src/main/java/com/flashsale/product/ProductService.java)；[CouponTemplateService.java](../flashsale-coupon/src/main/java/com/flashsale/coupon/CouponTemplateService.java)；Docker Java 21 测试通过 |
| 4 | 高 | 商品、优惠券更新采用延迟双删，并写审计和 Outbox | **已完成（R04）**：商品/优惠券写入先幂等删除消费者缓存，再在本地事务内提交业务数据、不可变审计和本服务 Outbox；payload 含资源类型、资源 ID、缓存键、Trace ID 和延迟删除计划，R16 负责实际投递与第二次删除 | [CacheInvalidator.java](../flashsale-common/src/main/java/com/flashsale/common/cache/CacheInvalidator.java)；[ProductWriteRepository.java](../flashsale-product/src/main/java/com/flashsale/product/ProductWriteRepository.java)；[CouponWriteRepository.java](../flashsale-coupon/src/main/java/com/flashsale/coupon/CouponWriteRepository.java)；Docker Java 21 `mvn -B -pl flashsale-product,flashsale-coupon -am test`：通过 |
| 5 | 高 | 活动预热由调度任务执行，写活动详情和库存键，并发送预热完成事件 | 创建活动时立即预热；`preheat` 接口没有执行预热；没有活动详情缓存和预热完成事件 | [ActivityService.java:20-26](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityService.java#L20)；[ActivityService.java:50-55](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityService.java#L50)；[RedisActivityInventory.java:37-42](../flashsale-activity/src/main/java/com/flashsale/activity/RedisActivityInventory.java#L37) |
| 6 | 高 | 活动开始前确认预热键存在，不能覆盖实时 Redis 库存 | `start` 直接调用 `inventory.rebuild`，会覆盖 Redis 库存、状态和门闸；没有预热键存在性校验 | [ActivityService.java:59-67](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityService.java#L59)；[RedisActivityInventory.java:68-72](../flashsale-activity/src/main/java/com/flashsale/activity/RedisActivityInventory.java#L68) |
| 7 | 高 | 暂停需先进入屏障流程：关闭门闸、等待在途 Lua 请求完成或补偿，再截取 barrier | 当前关闭门闸后立即锁序号并写入 `PAUSED`，没有在途请求计数、等待、补偿确认或独立 `PAUSING` 阶段 | [ActivityService.java:70-78](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityService.java#L70)；[ActivityStatus.java](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityStatus.java) |
| 8 | 高 | 暂停前 RESERVE、暂停期间 RELEASE 必须继续经 Outbox/MQ 消费 | 虽有活动库存事件追加逻辑，但未接入订单服务和 RocketMQ 消费链路；订单取消使用内存库存接口 | [ActivityInventoryService.java:19-36](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityInventoryService.java#L19)；[OrderCancellationService.java:10-17](../flashsale-order/src/main/java/com/flashsale/order/OrderCancellationService.java#L10) |
| 9 | 高 | 恢复需检查 Outbox、checkpoint、账本、流水、有效保留和 Redis；Redis 已存在时不得覆盖 | 恢复 worker 只检查未发送事件和 checkpoint，随后无条件重建 Redis，没有完整对账和“键存在则保留”逻辑 | [ActivityRecoveryWorker.java:7-8](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityRecoveryWorker.java#L7) |
| 10 | 高 | 恢复任务需要每活动恢复锁、告警，并在成功后写 `ACTIVITY_RESUMED` Outbox | 当前没有显式恢复锁；失败只更新任务状态并关闭门闸；成功没有恢复 Outbox 或告警接线 | [ActivityRecoveryWorker.java:7-8](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityRecoveryWorker.java#L7) |
| 11 | 高 | 订单创建使用 PostgreSQL 本地事务，写订单、预占、锁券、幂等记录、活动事件和 Outbox | 订单服务使用 `ConcurrentHashMap` 保存幂等记录，未写数据库、锁券、活动事件或 Outbox | [OrderService.java:9-15](../flashsale-order/src/main/java/com/flashsale/order/OrderService.java#L9)；[OrderService.java:25-56](../flashsale-order/src/main/java/com/flashsale/order/OrderService.java#L25) |
| 12 | 高 | 取消/超时释放使用数据库 CAS，只释放一次，并产生活动 RELEASE 事件 | 当前使用 `synchronized` 和内存订单，先释放库存再保存取消状态；没有数据库 CAS、活动 RELEASE Outbox 或补偿记录 | [OrderCancellationService.java:10-17](../flashsale-order/src/main/java/com/flashsale/order/OrderCancellationService.java#L10) |
| 13 | 高 | 支付幂等、回调、库存确认、券核销、履约和支付事件在事务中完成 | 支付服务使用内存 Map/Set 做幂等，支付成功直接把订单改为 `COMPLETED`；没有数据库支付记录、Outbox、库存确认和券核销事务 | [PaymentService.java](../flashsale-payment/src/main/java/com/flashsale/payment/PaymentService.java) |
| 14 | 高 | 优惠券领取使用 Redis Lua 完成高并发预扣和限购 | 当前直接更新 PostgreSQL 的发行数量和用户计数表，没有 Redis Lua 预扣 | [CouponClaimService.java:3-7](../flashsale-coupon/src/main/java/com/flashsale/coupon/CouponClaimService.java#L3) |
| 15 | 中 | 领券幂等使用规范化请求指纹，并区分冲突、处理中和失败状态 | 当前指纹为 `Integer.toHexString(key.hashCode())`，不是规范化 SHA-256；重复插入时捕获所有异常并统一处理 | [CouponClaimService.java:4](../flashsale-coupon/src/main/java/com/flashsale/coupon/CouponClaimService.java#L4) |
| 16 | 高 | 每个服务拥有真实 Outbox 投递器，支持租约、批量扫描、RocketMQ 发送和失败重试 | 数据库 Outbox 表存在，但公共 `OutboxDispatcher` 主要由内存测试实现支撑；未发现各服务生产 repository、dispatcher bean 或定时投递接线 | [OutboxDispatcher.java](../flashsale-common/src/main/java/com/flashsale/common/messaging/OutboxDispatcher.java)；[InMemoryOutbox.java](../flashsale-common/src/main/java/com/flashsale/common/messaging/InMemoryOutbox.java) |
| 17 | 高 | MQ 消费端使用独立幂等表，并与业务变更在同一 PostgreSQL 事务中提交 | 公共幂等实现是内存版本；实际业务消费者接线不足，活动消费者也没有 RocketMQ adapter 的生产配置 | [InMemoryConsumerIdempotency.java](../flashsale-common/src/main/java/com/flashsale/common/messaging/InMemoryConsumerIdempotency.java)；[ActivityInventoryConsumer.java:10-28](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityInventoryConsumer.java#L10) |
| 18 | 高 | 商品、活动、优惠券和账户变更都写 `operator_audit_log` | 当前能确认的审计写入主要在账户管理；商品、活动、优惠券服务没有对应审计写入 | [AdminAccountService.java](../flashsale-auth/src/main/java/com/flashsale/auth/AdminAccountService.java)；[ProductService.java:5-7](../flashsale-product/src/main/java/com/flashsale/product/ProductService.java#L5)；[ActivityService.java:20-26](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityService.java#L20) |
| 19 | 中 | 缓存失效事件包含资源类型、资源 ID、缓存键和 Trace ID | 活动创建事件只包含 `activity_id`；商品和优惠券没有对应缓存失效事件 | [ActivityRepository.java:30-35](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityRepository.java#L30) |
| 20 | 高 | 使用 XXL-Job 执行扫描、超时、对账和补偿 | 当前恢复任务使用本地 `@Scheduled`；`ReconciliationTask` 是通用内存规则执行器，没有 XXL-Job executor 接入 | [ActivityRecoveryWorker.java:7](../flashsale-activity/src/main/java/com/flashsale/activity/ActivityRecoveryWorker.java#L7)；[ReconciliationTask.java](../flashsale-job/src/main/java/com/flashsale/job/ReconciliationTask.java) |
| 21 | 高 | 使用 SkyWalking、Prometheus、Loki、Grafana、Alertmanager 完成完整可观测性 | 只有 Actuator、少量消息指标和容器配置；未发现 SkyWalking agent/config，Prometheus 只抓取单一 `host.docker.internal:8080` | [prometheus.yml:6-10](../config/prometheus/prometheus.yml#L6)；[MessagingMetrics.java](../flashsale-common/src/main/java/com/flashsale/common/messaging/MessagingMetrics.java) |
| 22 | 中 | 结构化日志至少包含 trace/span/user/order/event/idempotency/outbox/error 字段 | Filter 只写入 MDC 的 `trace_id`；`StructuredLogContext` 没有业务调用接线，也没有 JSON 日志配置 | [TraceIdFilter.java:16-22](../flashsale-common/src/main/java/com/flashsale/common/trace/TraceIdFilter.java#L16) |
| 23 | 高 | 监控请求吞吐、延迟、库存、领券、订单、支付、积压、重试、死信和补偿 | 当前指标类主要覆盖 Outbox、消费者和补偿，没有请求、库存预扣、领券、订单和支付指标 | [MessagingMetrics.java](../flashsale-common/src/main/java/com/flashsale/common/messaging/MessagingMetrics.java) |
| 24 | 高 | 订单、支付、库存服务提供对应 HTTP 业务入口 | 这些模块只有领域类或应用启动类，没有 Controller；Gateway 虽配置上游路由，但目标服务没有业务路由实现 | [flashsale-order/src/main/java](../flashsale-order/src/main/java)；[flashsale-payment/src/main/java](../flashsale-payment/src/main/java)；[flashsale-inventory/src/main/java](../flashsale-inventory/src/main/java) |

## 优先处理建议

建议按以下顺序收敛偏离：

1. 先补齐订单、支付、库存的 PostgreSQL 事务边界和 HTTP 入口，打通订单创建、取消、支付、库存释放闭环。
2. 完成各服务 Outbox repository、投递任务、RocketMQ producer/consumer 和数据库幂等记录接线。
3. 修正活动暂停/恢复屏障，补充在途请求清算、连续 checkpoint、账本对账和 Redis 键保护。
4. 为商品、优惠券模板补充 Cache Aside、延迟双删、缓存失效事件和运营审计。
5. 最后补齐 Sentinel、Nacos、XXL-Job、SkyWalking、结构化日志和业务指标的运行时接入。
