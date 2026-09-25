---
name: flashsale-h01-acceptance
description: Execute FlashSale H01 HTTP contract acceptance in resource-aware Docker batches, then diagnose, repair, test, regress, commit, and clean up each failure.
---

# FlashSale H01 HTTP 验收与修复循环

当用户要求继续、执行或修复 FlashSale 的 H01 验收时使用本 skill。目标是把真实 HTTP 验收失败推进到可复现、已修复、已回归的结果；H01 未覆盖完所有矩阵前保持 `DOING`，不得提前标记 `DONE`。

## 开始前

1. 检查 `git status`，保留 `.codebase-memory/`、`AGENTS.md` 和用户已有的无关修改，不回滚它们。
2. 阅读行为基线：
   - `docs/task.md` 的 H01 行和当前执行位置；
   - `docs/api-design.md` 的路径、字段、角色、错误码和幂等要求；
   - `docs/sequence-design.md` 的鉴权、资源归属、状态和幂等时序；
   - `docs/cross-cutting-design.md` 的事务、Outbox、Trace、补偿和可观测性要求。
3. 声明本轮环境、数据集、通过阈值和计划启动的服务。H01 的通过阈值是：已覆盖接口返回规定 HTTP/业务响应、错误码、字段、角色隔离、资源归属、幂等结果和 Trace ID；失败场景必须有可定位日志。
4. 优先使用 codebase-memory MCP：项目未索引时先 `index_repository`，再按 `search_graph`、`trace_path`、`get_code_snippet` 定位代码；字符串、配置和 SQL 再使用 `rg`。

## 资源受控的 Docker 启动

不要使用不加服务列表的 `docker compose up`，也不要一次启动所有服务。统一使用：

```bash
docker compose -f docker-compose.yml -f docker-compose.acceptance.yml \
  --profile r21 <command>
```

按以下层次启动，层内最多启动两个业务服务：

1. `postgres`，等待健康；运行一次性 `migration`，确认 Flyway 成功。
2. `redis`、`nacos`、`rocketmq-namesrv`，逐项等待健康。
3. `rocketmq-broker`，等待健康；不启动 `rocketmq-init` 以外的观测组件。
4. 业务服务分批启动：`auth product`、`activity inventory`、`coupon order`、`payment gateway`。每批完成健康检查后才启动下一批。

每个服务或批次后执行：

```bash
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
```

默认资源观察线：总内存接近 80% 或单批启动导致明显持续抖动时暂停下一批；记录峰值和原因，必要时改为逐个启动。H01 不需要启动 Prometheus、Grafana、Loki、SkyWalking、XXL-Job、MinIO 等非必要服务。

## HTTP 验收矩阵

使用独立的验收账号和可追踪的测试数据，至少覆盖：

- Auth：注册、重复注册、登录、JWT 角色和 Trace ID。
- 商品：创建、更新、上架/下架、公开列表/详情、运营列表、分页和非法参数。
- 优惠券：创建、更新限制、暂停/恢复、可领取列表、领券、我的券、重复领券和幂等冲突。
- 活动：创建、公开/运营查询、取消、暂停、恢复任务、恢复查询、指标和非法状态迁移。
- 订单：预览、创建、列表、详情、订单明细、取消、缺少/重复/冲突幂等键、跨用户访问。
- 支付：成功支付、重复支付、金额/币种错误、缺少幂等键、回调和回调幂等。
- 库存：预占、确认、释放入口、角色校验、非法数量和资源不存在。
- 角色：CUSTOMER 不能访问运营接口，ADMIN 不能修改业务配置，OPERATOR 不能管理账户；业务服务必须继续执行 JWT 和资源归属校验。

每个请求检查响应 `code`、`message`、`data` 字段、snake_case 输入映射、分页字段、Trace ID 和规定错误码；写请求同时检查 PostgreSQL 业务记录、审计记录和 Outbox 是否存在。

## 失败定位与修复循环

对每个失败严格按以下顺序处理：

1. 保存请求、响应、Trace ID、容器名和时间。
2. 读取对应服务最近日志，并查询 PostgreSQL/Redis 中与 Trace 或测试数据有关的状态；排除 SkyWalking 未启动产生的无关告警。
3. 用 MCP 追踪 Controller → Service → Repository/Feign/Outbox 调用链，确认是代码、配置、镜像入口、数据库类型还是验收数据问题。
4. 先把失败固定为可执行检查或针对性测试；不要直接猜测修改。
5. 做最小修改，严格遵守 `sequence-design.md` 和 `cross-cutting-design.md`；不得跳过事务、CAS、Outbox、幂等表、资源归属或二次鉴权。
6. 修改后立即运行 Docker Java 21 的对应 Maven 测试，并运行 `git diff --check`。
7. 只重建和重启受影响的服务，重新执行失败场景及相邻回归场景；确认资源仍稳定。
8. 每个独立修复单独 Git commit，提交信息使用明确的 `fix:`、`test:` 或 `docs:` 前缀。

## 完成本轮验收

1. 更新 `docs/task.md` 的 H01 验证列：记录真实通过项、修复提交、资源峰值、未启动的非必要服务、清理结果和剩余场景；未全部通过时保持 `DOING`。
2. 只有所有 H01 矩阵、异常、权限、资源归属和幂等场景通过后，才可将 H01 标记 `DONE` 并切换当前执行位置。
3. 停止本轮启动的验收服务，至少执行：

```bash
docker compose -f docker-compose.yml -f docker-compose.acceptance.yml \
  --profile r21 stop auth product activity inventory coupon order payment gateway \
  rocketmq-broker rocketmq-namesrv nacos postgres redis
```

4. 最终报告包含：通过和失败场景、代码/配置修复、测试命令和结果、Git 提交、资源峰值、服务清理状态，以及留给下一轮的明确场景。

