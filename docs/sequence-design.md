# 关键业务时序设计（一期）

## 1. 请求入口、认证与角色隔离

```mermaid
sequenceDiagram
    participant Guest as 游客
    participant Gateway as Gateway
    participant Auth as 用户服务
    participant DB as PostgreSQL

    Guest->>Gateway: POST /auth/register
    Gateway->>Gateway: Sentinel 按接口/IP 限流
    Gateway->>Auth: 注册请求（不接受 role）
    Auth->>DB: 创建 app_user(role=CUSTOMER)
    DB-->>Auth: 成功
    Auth-->>Guest: 注册成功

    Guest->>Gateway: POST /auth/login
    Gateway->>Gateway: Sentinel 按接口/IP 限流
    Gateway->>Auth: 登录请求
    Auth->>DB: 校验用户名、密码与 ACTIVE 状态
    DB-->>Auth: 账户角色
    Auth-->>Guest: JWT(user_id, role)

    Note over Gateway,Auth: Gateway 与服务均校验角色；服务从 JWT 取得当前账户
```

`CUSTOMER` 只能操作自己的订单和优惠券；`OPERATOR` 只能管理商品、活动、优惠券；`ADMIN` 只能管理账户与角色。角色不匹配返回 `FORBIDDEN`，资源不属于当前消费者返回 `RESOURCE_ACCESS_DENIED`。

文字说明：

1. 注册入口不允许调用方提交角色、状态或操作者字段，服务端只创建可用的 `CUSTOMER` 账户。
2. 登录仅为启用账户签发 JWT；被禁用账户不能获得新的访问令牌。
3. Gateway 是第一层拦截，业务服务必须再次校验 JWT 角色和资源归属，防止绕过网关或内部调用越权。
4. `OPERATOR` 与 `ADMIN` 的权限不互通：前者管理业务配置，后者管理账户与角色。
5. 所有请求在 Gateway 经过 Sentinel 限流；活动下单和领券还必须配置更细的接口/热点参数限流。被限流请求直接返回 `RATE_LIMITED`，不得进入 Redis、数据库或 MQ 主流程。
6. 所有关键流程透传 Trace ID，并记录结构化日志和业务指标；SkyWalking 链路、Prometheus 指标、Loki 日志与 Alertmanager 告警的组件配置和指标口径保留在 `cross-cutting-design.md`，不为每条业务时序重复绘制。

## 2. 商品/优惠券模板读缓存

```mermaid
sequenceDiagram
    participant Customer as 消费者
    participant Service as 商品/优惠券服务
    participant Redis as Redis读缓存
    participant DB as PostgreSQL

    Customer->>Service: 查询商品或可领券模板
    Service->>Redis: GET 缓存键
    alt 缓存命中
        Redis-->>Service: 返回可见配置
        Service-->>Customer: 返回结果
    else 缓存未命中
        Service->>Redis: SET cache:lock:<key> NX PX（携带 owner token）
        alt 获得缓存重建锁
            Service->>Redis: 二次 GET 缓存键
            alt 二次检查仍未命中
                Service->>DB: 查询 ON_SALE 商品或可领取模板
                DB-->>Service: 返回配置
                Service->>Redis: SET 缓存（TTL + 随机抖动）
            end
            Service->>Redis: Lua 校验 owner token 后释放锁
            Service-->>Customer: 返回结果
        else 未获得锁
            Service->>Redis: 短暂退避后重试 GET 缓存键
            Redis-->>Service: 返回重建后的缓存或受控失败
            Service-->>Customer: 返回结果或稍后重试
        end
    end

```

文字说明：

1. 非活动商品详情/列表和可领取优惠券模板采用 Cache Aside 读缓存；缓存只加速读取，不作为库存、券状态或业务审计的权威来源。
2. 缓存未命中时先用 Redis `SET NX PX` 获取按缓存键粒度的重建锁；获得锁后必须二次检查缓存，只有仍未命中时才查询数据库并回填。释放锁使用 Lua 比对 owner token，不能直接删除他人锁。
3. 未获得锁的请求不查询数据库，只做短暂退避和有限次缓存重试；重试后仍未命中则受控失败/稍后重试，避免高并发请求穿透到数据库。缓存 TTL 加随机抖动，降低集中失效风险。
## 3. 商品与优惠券运营写入缓存失效

```mermaid
sequenceDiagram
    participant Operator as 运营人员
    participant Product as 商品服务
    participant Coupon as 优惠券服务
    participant Redis as Redis
    participant DB as PostgreSQL
    participant Timer as Outbox投递任务
    participant MQ as RocketMQ

    Operator->>Product: 更新或上下架商品
    Product->>Redis: 第一次 DEL 商品缓存
    Product->>DB: BEGIN；更新商品/审计/product_outbox
    DB-->>Product: COMMIT
    Timer->>MQ: 延时 PRODUCT_CACHE_INVALIDATE
    MQ-->>Redis: 第二次 DEL 商品缓存

    Operator->>Coupon: 更新或暂停/恢复优惠券模板
    Coupon->>Redis: 第一次 DEL 模板缓存
    Coupon->>DB: BEGIN；更新模板/审计/coupon_outbox
    DB-->>Coupon: COMMIT
    Timer->>MQ: 延时 COUPON_CACHE_INVALIDATE
    MQ-->>Redis: 第二次 DEL 模板缓存

```

文字说明：

1. 商品和优惠券模板的配置更新采用延迟双删：先删缓存，再在本地事务写配置、审计和所属服务 Outbox，最后通过延时事件执行第二次删除；服务端不直接回写缓存。
2. 商品下架、优惠券暂停/结束领取均属于消费者视图的逻辑删除，提交后立即失效对应缓存。第一阶段不提供物理删除接口。
3. 缓存失效事件允许重复执行；Redis `DEL` 天然幂等。Outbox、事件和缓存失效失败必须可监控和重试。

## 4. 活动取消与暂停屏障

```mermaid
sequenceDiagram
    participant Operator as 运营人员
    participant Activity as 活动服务
    participant Redis as Redis
    participant DB as PostgreSQL
    participant Timer as Outbox投递任务
    participant MQ as RocketMQ
    participant Inventory as 库存服务

    Operator->>Activity: 取消未开始活动
    Activity->>DB: CAS NOT_STARTED -> CANCELLED；写审计和 activity_outbox
    DB-->>Activity: COMMIT
    Activity->>Redis: DEL 活动详情/库存预热键

    Operator->>Activity: 暂停进行中活动
    Activity->>Redis: Lua 原子关闭 RESERVE 门闸，并拒绝后续新预扣
    Activity->>Activity: 等待已进入 Lua 的预扣请求完成提交或 Redis 补偿
    Activity->>DB: 锁定 activity_inventory_sequence，截取 pause_barrier_sequence=next-1
    Activity->>DB: CAS ACTIVE -> PAUSED；写审计、暂停屏障和 activity_outbox
    DB-->>Activity: COMMIT
    Activity->>Redis: DEL 活动详情缓存；保留库存/限购键
    Timer->>MQ: 投递 ACTIVITY_PAUSE_BARRIER
    MQ-->>Inventory: 继续接收 RESERVE、RELEASE 与屏障消息
    Inventory->>DB: 连续消费库存事件并推进检查点
```

文字说明：

1. 一期活动创建后不再修改商品、价格、库存、限购和时间配置；运营人员仅能取消未开始活动、暂停进行中活动、请求恢复已暂停活动。
2. 取消仅适用于 `NOT_STARTED → CANCELLED`，清理未投入使用的预热键；它不是暂停，不创建暂停/恢复屏障。
3. 暂停先关闭 Redis 的新 `RESERVE` 门闸，再等待已进入预扣 Lua 的请求全部完成：每个请求要么与订单、库存保留、`activity_inventory_event(RESERVE)`、`order_outbox` 同事务提交，要么完成 Redis 补偿。随后在数据库锁住 `activity_inventory_sequence` 并以 `next_event_sequence - 1` 截取 `pause_barrier_sequence`，最后才提交 `ACTIVE → PAUSED`。因此屏障前不会遗漏已接受且尚未入账的预扣。
4. 暂停前已提交的 `RESERVE`，以及暂停期间订单取消/超时产生的 `RELEASE`，必须继续经 Outbox/MQ 投递、由库存服务消费；不得因活动是 `PAUSED` 被丢弃、拒绝或回滚。暂停只失效活动详情缓存，保留库存和限购键，释放仍会回补实时库存。
5. 活动取消、暂停、恢复和开始状态变更都只允许活动服务执行；商品、订单和库存服务不得直接改活动状态。缓存失效与库存事件按至少一次投递，以状态 CAS、事件序号和消费者幂等处理重复。

## 5. 活动开始前预热与异步恢复

```mermaid
sequenceDiagram
    participant Scheduler as XXL-Job
    participant Activity as 活动服务
    participant DB as PostgreSQL
    participant Timer as Outbox投递任务
    participant MQ as RocketMQ
    participant Redis as Redis

    Scheduler->>Activity: 开始前预热窗口到达
    Activity->>DB: 读取 NOT_STARTED 活动配置和 available_stock
    Activity->>Redis: 初始化活动详情和库存键
    Activity->>DB: 写 ACTIVITY_PREHEAT_READY Outbox
    Timer->>MQ: 投递预热完成事件
    MQ-->>Activity: 消费预热事件
    Scheduler->>Activity: 到达 starts_at，要求激活活动
    Activity->>Redis: 校验活动预热键存在
    Activity->>DB: CAS: NOT_STARTED -> ACTIVE；写 ACTIVITY_STARTED Outbox

```

文字说明：

1. 调度任务在开始前预热窗口内以 PostgreSQL 的初始配置写入活动详情和库存键；仅当预热键存在，开始时刻的请求才可 CAS `NOT_STARTED → ACTIVE`。用户限购计数按首次参与活动由 Lua 初始化，不做全量预热。
2. 预热只适用于从未开始的活动。活动进行中 Redis 是实时可售库存权威，`marketing_activity.available_stock` 是可滞后的投影，不能在恢复时直接覆盖 Redis。

```mermaid
sequenceDiagram
    participant Operator as 运营人员
    participant Activity as 活动服务
    participant Worker as 恢复任务
    participant DB as PostgreSQL
    participant Outbox as Outbox投递任务
    participant MQ as RocketMQ
    participant Inventory as 库存服务
    participant Redis as Redis

    Operator->>Activity: POST /activities/{id}/resume
    Activity->>DB: 创建 activity_recovery_job(PENDING)
    Activity-->>Operator: 202 Accepted（活动仍为 PAUSED）
    Worker->>DB: 领取任务；获取每活动恢复锁；标记 RUNNING
    Worker->>DB: 锁定 sequence，截取 recovery_barrier=next-1
    Worker->>DB: 校验 <= barrier 的库存事件对应 Outbox 均为 SENT
    alt Outbox 未全部 SENT 或检查点未追平
        Worker->>Outbox: 促使待发送事件重试
        Outbox->>MQ: 发送 RESERVE/RELEASE
        MQ-->>Inventory: 消费库存事件
        Inventory->>DB: 连续推进 checkpoint
        Worker->>DB: 保持 RUNNING，稍后重试
    else Outbox 已发送且 checkpoint 已连续追平
        Worker->>DB: 对账事件账本、流水、有效保留与库存投影
        alt Redis 库存键存在
            Worker->>Redis: 保留实时库存键，仅预热活动详情
        else Redis 库存键丢失
            Worker->>Redis: 持恢复锁，以对账通过的投影重建库存键并预热详情
        end
        Worker->>DB: CAS PAUSED -> ACTIVE；写 ACTIVITY_RESUMED Outbox；任务 SUCCEEDED
    else 对账或预热失败
        Worker->>DB: 任务 FAILED；活动保持 PAUSED
        Worker->>Worker: 记录告警
    end
```

3. 恢复为异步操作。恢复任务截取的是当前已提交事件账本的 `recovery_barrier_sequence`；它覆盖暂停前预扣及暂停期间全部已产生的释放。每个不大于该屏障的事件必须先确认其生产端 Outbox 已 `SENT`，并且 `activity_inventory_checkpoint.last_contiguous_sequence` 连续追平该屏障。
4. 只有上述条件和账本对账都成功后才可预热详情并 CAS `PAUSED → ACTIVE`。Redis 库存键存在时绝不覆盖；键丢失时也只能在恢复互斥锁下，用已追平且已对账的投影重建。失败时任务为 `FAILED`、触发告警且活动持续 `PAUSED`。
5. 恢复任务运行期间，暂停状态继续拒绝新预扣，但所有既有订单支付、取消、超时以及其 `RELEASE` 均继续处理；它们在恢复屏障之后到达时由下一次恢复任务重新截取并验证。

## 6. 创建订单与 Redis 预扣

```mermaid
sequenceDiagram
    participant Customer as 消费者
    participant Order as 订单服务
    participant Redis as Redis
    participant DB as PostgreSQL
    participant Timer as Outbox投递任务
    participant MQ as RocketMQ

    Customer->>Order: POST /orders + Idempotency-Key
    Order->>DB: 查询/抢占下单幂等记录
    alt 同键已成功
        DB-->>Order: 首次订单结果
        Order-->>Customer: 返回首次结果
    else 新请求
        Order->>Order: 校验商品/活动/券与订单金额
        Order->>Redis: Lua 原子预扣库存与活动限购
        alt 预扣失败
            Redis-->>Order: 库存不足或超限
            Order-->>Customer: 创建失败
        else 预扣成功
            Redis-->>Order: 预扣成功
            Order->>DB: BEGIN；写订单、订单项、预占、锁券、幂等记录、活动 RESERVE 事件、order_outbox
            alt 本地事务失败
                DB-->>Order: ROLLBACK
                Order-->>Customer: 创建失败
                Note over Redis,DB: 对账任务后续回补 Redis 预扣
            else 本地事务成功
                DB-->>Order: COMMIT
                Order-->>Customer: 待支付订单（15分钟到期）
                loop 扫描待投递 Outbox
                    Timer->>DB: 领取 PENDING/FAILED 事件
                    Timer->>MQ: 发送订单事件
                    MQ-->>Timer: 发送结果
                    Timer->>DB: 成功标记 SENT；失败退避重试
                end
            end
        end
    end
```

文字说明：

1. 下单幂等以 `(user_id, Idempotency-Key)` 和请求指纹判定；同键同请求重试返回首次结果，同键不同请求拒绝。
2. 订单服务在预扣前校验商品上架、活动时间窗口、限购、优惠券归属/有效期/门槛及金额；活动边界以 PostgreSQL 服务器时间为准。
3. Redis 预扣成功后，订单、订单项、库存保留、优惠券锁定、下单幂等记录和 `order_outbox` 必须同一个本地事务提交。活动订单还在该事务锁定 `activity_inventory_sequence`、分配连续序号，并写 `activity_inventory_event(RESERVE)`；它与承载该事件的 `order_outbox` 关联。任一数据库写入失败时，不创建订单。
4. Redis 已预扣但本地事务失败时允许短暂不一致；对账任务以 PostgreSQL 业务记录为最终权威回补 Redis。
5. 用户收到下单成功时订单已持久化，但事件仍可能尚未投递；Outbox 负责后续可靠投递，不阻塞用户响应。

## 7. 领取优惠券

```mermaid
sequenceDiagram
    participant Customer as 消费者
    participant Coupon as 优惠券服务
    participant Redis as Redis
    participant DB as PostgreSQL

    Customer->>Coupon: POST /coupons/{templateId}/claims + Idempotency-Key
    Coupon->>DB: 原子抢占 coupon_claim_idempotency
    alt 同键已有结果
        DB-->>Coupon: 返回首次成功/失败/处理中结果
        Coupon-->>Customer: 复用首次结果
    else 首次请求
    Coupon->>Redis: Lua 校验领取窗口、库存和个人限领并预扣
    alt Redis 失败
        Redis-->>Coupon: 已领完/超限/不可领取
        Coupon-->>Customer: 领取失败
    else Redis 成功
        Coupon->>DB: BEGIN；写 user_coupon、领取计数、coupon_outbox
        alt 提交失败
            DB-->>Coupon: ROLLBACK
            Coupon-->>Customer: 领取失败
            Note over Redis,DB: 对账补偿 Redis 库存与限领计数
        else 提交成功
            DB-->>Coupon: COMMIT
            Coupon-->>Customer: 返回用户券
        end
    end
    end
```

文字说明：

1. 领券请求先以 `(user_id, coupon_template_id, Idempotency-Key)` 原子抢占 `coupon_claim_idempotency`；同键重试复用首次成功、失败或处理中结果，不会再次 Redis 预扣。
2. 首次请求再校验模板状态、领取时间、发行余量和个人限领数量；Redis Lua 将这些高并发判断和预扣合并为原子操作。
3. Redis 成功后，`user_coupon`、领取计数、领券请求幂等结果和 `coupon_outbox` 在 PostgreSQL 同一事务中写入，确保已发券必有可投递事件。
4. PostgreSQL 提交失败不能向消费者返回成功；Redis 的预扣余量和个人计数由对账任务恢复。MQ 事件重复投递时由消费端幂等记录表去重。

## 8. 支付与延时超时取消竞争

```mermaid
sequenceDiagram
    participant Customer as 消费者
    participant Order as 订单服务
    participant Payment as 支付服务
    participant Timer as Outbox投递任务
    participant DB as PostgreSQL
    participant MQ as RocketMQ

    Order->>DB: 创建订单事务：订单/预占/ORDER_PAYMENT_TIMEOUT Outbox
    DB-->>Order: COMMIT
    Timer->>MQ: 发送 15 分钟延时超时消息
    Customer->>Payment: POST /orders/{number}/payments + Idempotency-Key
    Payment->>DB: 原子抢占 payment_submission_idempotency
    alt 同键已有结果
        DB-->>Payment: 返回首次成功/失败/处理中结果
        Payment-->>Customer: 复用首次结果
    else 首次请求
        par 用户支付
            Payment->>DB: CAS: PENDING_PAYMENT -> PAID
        and 延时消息到达
            MQ-->>Order: ORDER_PAYMENT_TIMEOUT
            Order->>DB: CAS: PENDING_PAYMENT -> CANCELLED 且 expires_at <= now()
        end
        alt 支付 CAS 成功
            DB-->>Payment: 订单已支付
            Payment->>DB: 同事务确认库存/核销券/创建履约/payment_outbox/支付幂等结果
            Payment->>MQ: 由 Outbox 异步发送支付事件
            Payment-->>Customer: 支付成功，订单完成
        else 取消 CAS 成功
            DB-->>Order: 订单已取消
            Order->>DB: 同事务释放库存/活动名额/恢复或过期优惠券/写 Outbox
        else CAS 未命中
            Note over Payment,Order: 读取最终订单状态；不重复确认或释放资源
        end
    end
```

文字说明：

1. 支付请求先以 `(user_id, order_id, Idempotency-Key)` 原子抢占 `payment_submission_idempotency`；同键重试复用首次结果，不会再次发起支付处理。
2. 下单事务提交后，由 Outbox 投递 `ORDER_PAYMENT_TIMEOUT` 的 15 分钟 RocketMQ 延时消息；延时消息是超时取消的主触发路径。
3. 支付和延时消息消费都只能通过订单状态条件更新抢占 `PENDING_PAYMENT`，因此二者只有一个能成功。
4. 支付成功的事务要确认库存保留、核销已锁定优惠券、创建自动完成的履约记录、写支付 Outbox 并完成支付幂等结果；任一项失败则支付状态变更不提交。
5. 超时取消的事务释放对应库存；活动订单还要释放活动限购名额，并在同一事务分配活动事件序号、写 `activity_inventory_event(RELEASE)` 与 `STOCK_RELEASE` 的 `order_outbox`。锁定优惠券根据使用截止时间恢复为 `AVAILABLE` 或标记 `EXPIRED`。
6. 未抢到状态转换的一方只读取最终状态：已支付不再释放资源，已取消不再支付，避免重复确认或重复释放。
7. XXL-Job 只作为兜底对账：扫描 `expires_at <= now()` 但仍为 `PENDING_PAYMENT` 的订单，补偿延时消息投递、Broker 或消费者异常造成的遗漏。

## 9. 订单取消/超时后的活动库存回补

```mermaid
sequenceDiagram
    participant Caller as 用户或延时消息
    participant Order as 订单服务
    participant DB as PostgreSQL
    participant Outbox as Outbox投递任务
    participant MQ as RocketMQ
    participant Inventory as 库存服务
    participant Redis as Redis

    Caller->>Order: 取消请求或 ORDER_PAYMENT_TIMEOUT
    Order->>DB: CAS PENDING_PAYMENT -> CANCELLED
    alt CAS 成功且为活动订单
        Order->>DB: 同一事务：reservation=RELEASED、释放限购、分配事件序号
        Order->>DB: 写 activity_inventory_event(RELEASE) 和 STOCK_RELEASE order_outbox
        DB-->>Order: COMMIT
        Outbox->>MQ: 发送 STOCK_RELEASE(event_id, sequence)
        MQ-->>Inventory: 至少一次投递
        Inventory->>DB: 抢占 STOCK_<event_id> 消费幂等记录
        alt 首次处理
            Inventory->>Redis: Lua 按 event_id 幂等 INCRBY 活动库存
            Inventory->>DB: 写 RELEASE inventory_movement、更新投影和连续 checkpoint；幂等记录 SUCCEEDED
            DB-->>Inventory: COMMIT 后 ACK
        else 已成功
            Inventory-->>MQ: ACK，不再回补
        end
    else 已支付或已取消
        Order-->>Caller: 返回最终订单状态，不重复释放
    end
    Note over Redis,Inventory: PAUSED 期间仍执行 RELEASE；不重新打开 RESERVE 门闸
```

文字说明：

1. 用户取消与延时超时共用订单状态 CAS，只有取得 `PENDING_PAYMENT → CANCELLED` 的一方产生一次释放事件；其他调用只读最终状态。
2. 对活动订单，订单服务在取消事务中将保留记录改为 `RELEASED`、释放活动限购、锁定活动序号并写入 `activity_inventory_event(RELEASE)` 和同一条生产端 `order_outbox`。`activity_inventory_event.outbox_event_id` 指向该 Outbox 事件，故恢复任务可检查事件是否已投递。
3. 库存服务按 `STOCK_<event_id>` 消费幂等。Redis 回补 Lua 也保存事件标记，确保数据库提交失败后的消息重试不会重复 `INCRBY`；随后在一个 PostgreSQL 事务写库存流水、更新 `marketing_activity.available_stock` 投影和连续检查点，并将消费记录置为 `SUCCEEDED`。Redis 标记仅可在持久化成功且对账后按保留策略清理。
4. `RESERVE` 的 Redis 扣减已在下单 Lua 完成；库存服务消费其事件只写库存流水、投影和检查点，不再次扣减 Redis。`RELEASE` 即使活动暂停、恢复任务运行或决定不恢复，也必须继续回补 Redis 和推进检查点。

## 10. Outbox 重复投递与消费幂等

```mermaid
sequenceDiagram
    participant Timer as 投递任务
    participant DB as PostgreSQL
    participant MQ as RocketMQ
    participant Consumer as 消费服务

    Timer->>DB: 锁定 PENDING/FAILED Outbox（租约）
    Timer->>MQ: 发送 event_id + idempotency_key
    MQ-->>Consumer: 至少一次投递
    Consumer->>DB: 插入/抢占 PROCESSING 幂等记录
    alt 已 SUCCEEDED
        DB-->>Consumer: 已处理
        Consumer-->>MQ: ACK，不执行业务变更
    else 新消息
        Consumer->>DB: 业务变更 + SUCCEEDED 同一事务提交
        DB-->>Consumer: COMMIT
        Consumer-->>MQ: ACK
    else 失败
        Consumer->>DB: 标记 FAILED
        Consumer-->>MQ: 返回重试
    end
    Note over Timer,MQ: 发送成功但未标记 SENT 时会重复投递，由幂等记录拦截
```

文字说明：

1. Outbox 采用至少一次投递。投递任务通过状态、可投递时间和租约领取事件；发送失败记录错误并按退避时间重试。
2. 消息发送成功但 Outbox 更新为 `SENT` 前进程宕机时，下一次扫描会重复发送，这是预期行为。
3. 每个消费者服务用自身的幂等记录表以业务前缀幂等号抢占处理权。已 `SUCCEEDED` 的重复消息只 ACK，不执行业务变更。
4. 新消息的领域写入与幂等状态改为 `SUCCEEDED` 必须同一 PostgreSQL 事务提交。失败记录为 `FAILED` 并由 RocketMQ 重试；长时间 `PROCESSING` 由恢复任务处理。

## 11. 运营配置与账户管理

```mermaid
sequenceDiagram
    participant Operator as 运营人员
    participant Admin as 管理员
    participant Gateway as Gateway
    participant Service as 运营/用户服务
    participant DB as PostgreSQL

    Operator->>Gateway: 商品/活动/优惠券管理请求（OPERATOR JWT）
    Gateway->>Service: 转发并校验 OPERATOR
    Service->>DB: BEGIN；更新配置和 updated_by；写审计日志
    DB->>DB: 触发器校验操作者为活跃 OPERATOR
    DB-->>Service: COMMIT

    Admin->>Gateway: 账户创建、角色或状态变更（ADMIN JWT）
    Gateway->>Service: 转发并校验 ADMIN
    Service->>DB: BEGIN；更新 app_user；写 USER_ACCOUNT 审计
    DB->>DB: 触发器保护最后一个活跃 ADMIN
    DB-->>Service: COMMIT 或拒绝
```

文字说明：

1. 运营写请求的操作者只来自 OPERATOR JWT。服务不能采信前端提交的 `created_by`、`updated_by` 或审计快照。
2. 商品、活动、优惠券的配置更新、修改人更新和审计日志必须同一事务提交；数据库触发器拒绝非活跃 OPERATOR 作为配置操作者。
3. ADMIN 只能创建账户、修改角色和启停账户，同时写入目标类型为 `USER_ACCOUNT` 的审计记录；ADMIN 不拥有运营配置权限。
4. 应用层拒绝管理员修改/禁用自身；数据库还会拒绝移除、降级或禁用最后一个活跃 ADMIN，防止后台失去管理入口。

## 11. 对账与补偿

XXL-Job 关联 Redis 预扣记录、PostgreSQL 业务状态、Outbox 状态、MQ 消费状态和库存/优惠券流水。补偿操作必须记录原因、原状态、目标状态和 Trace ID，并使用 CAS 保证可重复执行而不重复扣减或释放。

文字说明：

1. Redis 与 PostgreSQL 的差异以 PostgreSQL 已提交的业务状态和完整库存流水为准；既要检查 Redis 有预扣而业务记录不存在，也要检查暂停期间释放事件是否已反映到 Redis。
2. 长时间未投递的 Outbox、长时间 `PROCESSING` 的消费记录、消息持续失败和死信消息均需告警并进入补偿队列。
3. 补偿任务可被重复执行，所有库存、优惠券和订单更新均带状态条件；补偿结果写入日志、指标和 Trace 关联字段以便审计。
