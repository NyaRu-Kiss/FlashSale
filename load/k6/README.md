# H03 k6 压测

压测脚本覆盖活动瞬时抢购、同用户限购、普通商品读取、普通商品购买、领券、重复下单和支付/取消竞争。活动暂停/恢复不属于 H03。

## 运行方式

使用 k6 Docker 镜像，业务服务只按当前场景启动。写场景使用独立商品、活动、券和用户数据。活动库存突发应通过 `LOAD_TOKENS` 传入多个 CUSTOMER JWT（逗号分隔），避免把单用户限购误当成全局库存容量；`LOAD_TOKEN` 适合单用户限购和重复幂等场景。

```bash
docker run --rm --network host \
  -e BASE_URL=http://127.0.0.1:8080 \
  -e LOAD_TOKEN="$LOAD_TOKEN" \
  -e PRODUCT_ID=101 -e ACTIVITY_ID=7 \
  -e SCENARIO=activity_burst \
  -v "$PWD/load/k6:/scripts:ro" \
  ghcr.io/grafana/k6:latest run /scripts/h03.js
```

## 场景参数

| 场景 | 关键参数 |
|---|---|
| `activity_burst` | 默认 10,000 请求/秒、持续 1 秒；可用 `BURST_RATE`、`BURST_DURATION`、`PREALLOCATED_VUS`、`MAX_VUS` 调整 |
| `activity_limit` | 默认 100 请求/秒、持续 30 秒；所有请求使用同一 CUSTOMER JWT、不同幂等键，验证同用户限购 |
| `product_read` | 默认 5,000 VUs、30 秒；可用 `VUS`、`DURATION` 调整 |
| `direct_purchase` | 固定阶梯 10→50→100 VUs |
| `coupon_claim` | 默认 100 VUs、30 秒；每次使用独立幂等键 |
| `duplicate_order` | 所有请求共用 `DUPLICATE_KEY`，验证同键幂等 |
| `payment_cancel` | 固定阶梯 10→50→100 VUs；必须提供逗号分隔的 `ORDER_NUMBERS` |

`LOAD_TOKENS=user-token-1,user-token-2,...` 按 VU 轮换身份；`LOAD_TOKEN` 未提供时，除公开商品读取外的场景会立即失败。

## 结果判定

k6 只设置通用失败率和脚本检查阈值，不预设 P95/P99 硬门槛。每个场景结束后必须核对 PostgreSQL、Redis、Outbox、消费幂等、库存流水和活动事件序号；保存 `summary.json`、Docker 资源峰值和场景数据快照。

1 万请求是 1 秒内的瞬时突发，不代表持续 1 万 QPS。若 k6 在单机上成为 CPU/内存瓶颈，结果只能作为单机压测结果，不能解释为服务容量上限。
