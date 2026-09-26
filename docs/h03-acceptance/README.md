# H03 高并发验收记录

## 项目亮点

> 在 500 QPS × 20 秒场景下，系统稳定处理了约 10,000 条请求，未出现超卖、重复有效订单或核心数据不一致。
>
> 在 1000 QPS × 10 秒场景下，受本机压测能力限制，实际完成 7,738 条请求，未达到完整 10,000 条；已处理请求仍未出现超卖或核心数据不一致。
>
> 这表明系统具备在高并发场景下保持库存安全、幂等和最终一致性的能力；在电脑及服务端资源允许的情况下，理论上可以安全承受 10,000 条请求的瞬时突发。

## 证据索引

- [500 QPS × 20 秒记录](records/500qps-x20s.md)
- [1000 QPS × 10 秒记录](records/1000qps-x10s.md)
- [最终对账记录](records/reconciliation.md)
- [k6 原始 summary 输出](evidence/k6/)
- [容器关键日志摘录](evidence/containers/)
- [完整容器日志（最近 2 小时）](evidence/containers-full/)
- 执行工件：[`load/h03/run.sh`](../../load/h03/run.sh)、[`load/k6/h03.js`](../../load/k6/h03.js)、[`load/h03/verify.sql`](../../load/h03/verify.sql)

## 原始证据

`evidence/k6/` 保留实际 k6 生成的 `summary-k6.json`，包含 iterations、http requests、dropped iterations、检查结果和延迟分位数。`evidence/containers/` 保留从实际 H03 容器读取的 XXL-Job、Outbox、消费者和 RocketMQ 关键日志摘录；日志中的基础设施告警没有被删除，只按关键字筛选以避免提交完整无关启动日志。

## 结论边界

当前机器没有完成 1 秒内 10,000 请求的实测，因此该指标仍是基于已验证安全不变量和系统设计的理论容量判断，不是本机已达成的容量基线。

证据范围：`500 QPS × 20 秒` 场景有完整 `verify.sql` 对账结果；`1000 QPS × 10 秒` 场景保留了 k6 请求计数、dropped iterations、活动成功订单和库存观察结果，但该场景之后未单独重复完整对账，因此相关表述应理解为“在已观察范围内未发现异常”。
