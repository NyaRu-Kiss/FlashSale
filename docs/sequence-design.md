# 关键业务时序设计（一期）

## 1. 认证与角色隔离

```mermaid
sequenceDiagram
    participant Guest as 游客
    participant Gateway as Gateway
    participant Auth as 用户服务
    participant DB as PostgreSQL

    Guest->>Gateway: POST /auth/register
    Gateway->>Auth: 注册请求（不接受 role）
    Auth->>DB: 创建 app_user(role=CUSTOMER)
    DB-->>Auth: 成功
    Auth-->>Guest: 注册成功

    Guest->>Gateway: POST /auth/login
    Gateway->>Auth: 登录请求
    Auth->>DB: 校验用户名、密码与 ACTIVE 状态
    DB-->>Auth: 账户角色
    Auth-->>Guest: JWT(user_id, role)

    Note over Gateway,Auth: Gateway 与服务均校验角色；服务从 JWT 取得当前账户
```

`CUSTOMER` 只能操作自己的订单和优惠券；`OPERATOR` 只能管理商品、活动、优惠券；`ADMIN` 只能管理账户与角色。角色不匹配返回 `FORBIDDEN`，资源不属于当前消费者返回 `RESOURCE_ACCESS_DENIED`。

## 2. 创建订单与 Redis 预扣

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
            Order->>DB: BEGIN；写订单、订单项、预占、锁券、幂等记录、order_outbox
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

## 3. 领取优惠券

```mermaid
sequenceDiagram
    participant Customer as 消费者
    participant Coupon as 优惠券服务
    participant Redis as Redis
    participant DB as PostgreSQL

    Customer->>Coupon: POST /coupons/{templateId}/claims + Idempotency-Key
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
```

## 4. 支付与超时取消竞争

```mermaid
sequenceDiagram
    participant Customer as 消费者
    participant Payment as 支付服务
    participant Job as XXL-Job
    participant DB as PostgreSQL
    participant MQ as RocketMQ

    par 用户支付
        Customer->>Payment: POST /orders/{number}/payments + Idempotency-Key
        Payment->>DB: CAS: PENDING_PAYMENT -> PAID
    and 超时任务
        Job->>DB: CAS: PENDING_PAYMENT -> CANCELLED
    end
    alt 支付 CAS 成功
        DB-->>Payment: 订单已支付
        Payment->>DB: 同事务确认库存/核销券/创建履约/payment_outbox
        Payment->>MQ: 由 Outbox 异步发送支付事件
        Payment-->>Customer: 支付成功，订单完成
    else 取消 CAS 成功
        DB-->>Job: 订单已取消
        Job->>DB: 同事务释放库存/活动名额/恢复或过期优惠券/写 Outbox
    else CAS 未命中
        Note over Payment,Job: 读取最终订单状态；不重复确认或释放资源
    end
```

## 5. Outbox 重复投递与消费幂等

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

## 6. 运营配置与账户管理

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

## 7. 对账与补偿

XXL-Job 关联 Redis 预扣记录、PostgreSQL 业务状态、Outbox 状态、MQ 消费状态和库存/优惠券流水。补偿操作必须记录原因、原状态、目标状态和 Trace ID，并使用 CAS 保证可重复执行而不重复扣减或释放。
