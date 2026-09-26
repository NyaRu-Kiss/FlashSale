# H03 执行流程与场景矩阵

## 阶段

1. 记录 `git status`、当前服务和主机 CPU/内存/Swap；读取四份设计文档。
2. 以独立 project 启动 `postgres`，等待健康，运行一次 `migration`；再按场景启动 `redis`、`nacos`、RocketMQ namesrv/broker/init 和业务服务。每批最多两个业务服务，批后记录 `docker stats --no-stream`。
3. 用 `load/h03/run.sh prepare` 或等价真实 API 创建独立商品、运营账号、活动和预热数据；用 `prepare-users.sh` 生成 CUSTOMER JWT 文件。
4. k6 通过 `load/h03/run.sh run` 执行；结果目录必须保留 `summary.json`、场景参数和资源采样。
5. 等待 Outbox/MQ 消费追平，执行 `run.sh verify` 及 reconciliation 查询，填充报告。
6. 若失败，按入口 Skill 的证据、调用链、固定测试、最小修复和回归顺序处理；每个小阶段独立提交。

## 场景矩阵

| 场景 | 必须证明 |
|---|---|
| `activity_burst` | 9,000 独立用户 + 1,000 重复请求；库存、限购、幂等、活动事件序号；固定 10,000 请求总量 |
| `activity_limit` | 同一 CUSTOMER 的限购边界，成功数不超过活动限购 |
| `product_read_cold` / `product_read_warm` | Cache Aside 冷启动、预热读取和字段完整性 |
| `direct_purchase` | 普通商品阶梯购买、库存流水和订单 Outbox |
| `coupon_claim` | 独立幂等键、领取上限和重复领取结果 |
| `duplicate_order` | 同键同请求只产生一个成功订单 |
| 冲突幂等 | 同键不同请求返回冲突，不修改原订单 |
| `payment_cancel` | 支付/取消竞争的 CAS、最终订单和支付状态 |
| 重复消息 | 消费幂等记录拦截重复投递，业务只生效一次 |

活动突发若因 VU 初始化、CPU 或内存保护降档，仍必须完成固定总请求量；记录实际窗口和 dropped iterations，不能宣称达到 1 秒目标。

## 资源保护

停止升档并保存证据的条件：可用内存接近 2 GiB、Swap 持续增长、CPU 持续接近 95%、k6 初始化被终止、数据库连接池耗尽、Redis 延迟异常、MQ/Outbox 积压不能追平或任一核心不变量破坏。压测期间每阶段采样主机和容器资源；只停止本轮启动的一次性服务，清理临时卷。
