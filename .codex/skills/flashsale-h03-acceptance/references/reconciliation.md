# H03 对账与判定

## PostgreSQL

执行 `load/h03/verify.sql` 全部查询并保存原始输出。所有 `violations`、`pending` 必须为 0；检查负库存、重复成功订单、活动事件序号连续、Outbox 全部 `SENT`、消费幂等全部 `SUCCEEDED`、库存预占均有订单。补充查询各业务 Outbox 最老消息年龄、失败/重试/死信和库存流水 checkpoint。

## Redis 与活动账本

记录活动实时库存、有效保留、用户限购计数与数据库投影；Redis 只作为预扣实时状态，最终结论以 PostgreSQL 业务记录、库存流水和事件 checkpoint 为准。活动暂停/恢复相关场景需证明屏障内事件连续追平，H03 普通场景不得复用耗尽或过期活动。

## Outbox/XXL-Job/RocketMQ

逐服务确认 Outbox 表状态、handler 注册、XXL-Job Admin 外部周期任务、最老消息年龄、发送失败/重试/死信和消费幂等状态。仅注册 handler 而无外部调度任务是测试环境配置阻塞，报告中单独列出；不得将未投递状态视为通过。

## 报告判定

报告必须包含 Compose project、临时卷、服务列表、资源峰值、场景参数、固定请求总数、实际发送窗口、吞吐与分位延迟、错误率、dropped iterations、SQL 原始结果、Redis/MQ/Outbox 快照和清理状态。任何缺失证据、突发窗口超目标或资源保护触发都要明确标注，不得用平均吞吐掩盖窗口未达标。
