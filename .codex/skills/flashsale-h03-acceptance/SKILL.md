---
name: flashsale-h03-acceptance
description: Execute FlashSale H03 high-concurrency acceptance with isolated Docker data, real API scenarios, resource guards, PostgreSQL/Redis/Outbox/MQ reconciliation, and minimal repair regression. Use for H03 capacity, flash-sale, inventory, limit, idempotency, or Outbox/MQ acceptance; do not use for ordinary unit tests or production load tests.
---

# FlashSale H03 高并发验收

本 Skill 把 H03 验收推进到可复核结论：环境准备、真实业务数据、k6 固定请求量、资源保护、最终对账、失败定位、最小修复、Docker Java 21 回归、文档和小步提交。H03 在 AC01～AC08、最终对账和资源证据齐全前保持 `DOING`，不得提前改为完成。

## 必须先读取

开始前读取 `docs/task.md`、`docs/api-design.md`、`docs/sequence-design.md`、`docs/cross-cutting-design.md`。代码发现优先使用 codebase-memory MCP：未索引时先 `index_repository`，再用 `search_graph`、`trace_path`、`get_code_snippet`；字符串、配置、SQL 和脚本才用 `rg`。检查 `git status`，保留用户已有修改、`AGENTS.md` 与 `.codebase-memory/`。

## 固定工件与环境

- 复用 `load/h03/docker-compose.h03.yml`、`load/h03/run.sh`、`load/h03/verify.sql`、`load/k6/h03.js`；不得复制出第二套压测逻辑。
- 使用独立 Compose project（默认 `flashsale-h03`）和临时 PostgreSQL/Redis/RocketMQ 卷。只启动当前场景服务，禁止无服务列表的 `docker compose up`。
- 统一通过 `docker compose -p flashsale-h03 -f docker-compose.yml -f load/h03/docker-compose.h03.yml --profile r21 ...`；迁移和验收 Maven 服务使用 `flashsale-m2:/root/.m2`，不使用主机 `~/.m2`。
- H03 专用 Sentinel 覆盖只用于验收数据准备和压测；不得把压测限流配置带入生产配置。
- JWT 大量用户使用只读 token 文件挂载；不要展开到环境变量。通过真实 API 创建独立商品、运营账号、活动、CUSTOMER JWT 和场景数据。
- 活动必须先创建为将开始状态，执行预热，确认 `ACTIVE` 后才进行活动下单；每个场景使用独立商品、活动、券、幂等键和可复核数据。

详细阶段顺序、场景矩阵和资源阈值见 [references/workflow.md](references/workflow.md)。数据库/Redis/Outbox/MQ 判定见 [references/reconciliation.md](references/reconciliation.md)。

## 结果和失败处理

每个场景保存 k6 `summary.json`、请求总数、吞吐、P50/P95/P99、错误率、实际发送窗口、dropped iterations、主机/容器资源峰值、连接池、Redis 延迟和 MQ/Outbox 积压。活动突发首选 10,000 请求在 1 秒窗口；资源不足时可降档但必须完成固定总请求量，并明确标记“未达到目标突发窗口”，不能把 dropped iterations 当成功。

对失败按顺序执行：保存证据 → 读取服务日志和数据库/Redis 状态 → 用 MCP 分析调用链 → 固定为可执行测试 → 最小修改（遵守两个设计文档中的事务、CAS、Outbox、幂等、屏障和二次鉴权）→ Docker Java 21 受影响测试 → 只重建受影响服务并复跑失败场景及相邻回归 → 更新报告和 `docs/task.md` → 独立 Git 小步提交。每次修改后先测试，提交前执行 `git diff --check`。

若 Outbox 有 handler 但没有外部 XXL-Job 调度任务，判为测试环境配置阻塞并记录证据；确认是业务缺陷才进入修复回归，不得静默跳过。所有一次性测试服务和临时卷在验证完成后关闭/删除；预先存在的调试服务按当前会话策略保留，不得误删 acceptance 数据卷。

## 验证入口

先运行 skill-creator 的 `quick_validate.py`，再做静态路径/命令检查和 H03 dry-run（Compose 合并、服务选择、Sentinel、token 只读挂载、k6 参数、SQL）。运行方式与报告字段以 `load/k6/README.md` 和 `load/h03/report-template.md` 为准。不得把未执行的场景或缺少资源/对账证据写成通过。
