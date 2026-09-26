# H03 最终对账记录

执行工件：[`load/h03/verify.sql`](../../../load/h03/verify.sql)

## PostgreSQL 对账结果

| 检查项 | 结果 |
|---|---:|
| `negative_product_stock` | 0 |
| `negative_activity_stock` | 0 |
| `duplicate_successful_orders` | 0 |
| `activity_event_sequence_gaps` | 0 |
| `outbox_not_sent` | 0 |
| `consumer_not_succeeded` | 0 |
| `reservation_without_order` | 0 |

## Outbox 与幂等

- 六个业务服务均注册 `outboxDispatch` executor。
- H03 临时 XXL-Job MySQL 已幂等创建每秒调度任务。
- Admin 与 executor 使用统一 `default_token`。
- 历史 `PENDING` Outbox 已追平为 `SENT`。
- 重复下单和冲突幂等结果符合设计。
- 优惠券单用户限领只生成一张用户券，Coupon Outbox 为 `SENT`。
