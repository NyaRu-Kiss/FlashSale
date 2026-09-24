---
name: flashsale-r-deviation
description: Execute one FlashSale Rxx design-deviation repair at a time, using the project documents, incremental tests, Docker verification, documentation updates, and a compact handoff for the next R task.
---

# FlashSale Rxx 修正流程

在 FlashSale 仓库中用户要求“继续下一个 R”、修复设计偏离，或按既定 R 流程开发时使用本 skill。一次只处理 `docs/task.md` 中当前最早的未完成 `Rxx`；不要跨 R 顺手修复其他偏离。

## 开始前

1. 先检查工作区状态，保留用户已有的无关修改，不回滚它们。
2. 阅读并以这三份文档为行为基线：
   - `docs/task.md`：当前 R 的范围、三方依据、验证要求和状态。
   - `docs/sequence-design.md`：业务时序、状态机、缓存和屏障。
   - `docs/cross-cutting-design.md`：事务、Outbox、幂等、补偿、消息和可观测性。
   必要时阅读 `docs/design-deviation-checklist.md` 的对应偏离项。
3. 将当前 R 标记为 `DOING`，并注明本次只修复哪个偏离。
4. 优先使用 codebase-memory MCP 进行代码发现：项目未索引时先索引；按 `search_graph`、`trace_path`、`get_code_snippet`、`query_graph` 的顺序定位实现。只有 MCP 对字符串、配置或非代码文件不足时才使用 `rg` 等本地搜索。
5. 修改前若需求、边界或三方依据存在无法合理推断的冲突，先向用户提问；否则按现有代码模式做最小实现。

## 实现循环

- 先把当前 R 的验收场景转成失败测试或可执行检查，覆盖适用的正常、重复、并发、失败、重试和恢复路径。
- 小步修改：每个可独立验证的阶段完成后立即运行针对性测试，并单独提交；不要等多个 R 或大量无关重构完成后再提交。
- 严格遵循三方文档。不得以内存 `Map`、`synchronized`、本地 `@Scheduled`、直接跨服务数据库写入或同步 MQ 发送替代规定的 PostgreSQL 本地事务、Outbox、RocketMQ、幂等表和补偿任务。
- 保持低耦合高内聚和开闭原则，优先复用现有公共组件；不为一次性逻辑引入过度抽象。
- 测试服务或容器只在验证期间运行。验证完成后关闭本次启动的服务；容器优先使用 `--rm`，长时间命令使用可控超时。

## 完成当前 R

完成实现后：

1. 按 `docs/task.md` 记录的命令在 Docker Java 21 环境验证；若新增 PostgreSQL/Redis 集成测试，使用临时测试容器并清理。
2. 运行 `git diff --check`。
3. 更新 `docs/task.md`：当前 R 标为 `DONE`，填写实际验证结果，并把“当前执行位置”切换到下一个 R。
4. 更新 `docs/design-deviation-checklist.md`：对应偏离标记为已完成，并附代码/测试证据。
5. 检查差异，只提交当前 R 的代码、测试和文档；使用独立的小步 Git commit。不要提交 `.codebase-memory/`、`AGENTS.md` 或其他无关变更。

## 压缩交接协议

Skill 不能直接管理或压缩 Codex 的隐藏上下文，也不能自动执行 `/compact`。完成一个 R 后，在回复末尾提供以下短摘要，然后等待或建议用户执行 `/compact`；压缩后继续下一个 R 时，先重新读取三方文档和该摘要，不依赖被清空的代码细节记忆。

```text
Rxx 已完成：<偏离编号和一句话结果>
下一任务：Ryy（当前状态和范围）
文档依据：task.md <位置>；sequence-design.md <章节>；cross-cutting-design.md <章节>
提交：<commit>
验证：<命令和结果>
未完成/排除：<明确列出留给后续 R 的内容>
```

压缩后新一轮的第一步仍是确认工作区、读取文档、确认最早未完成 R 和三方边界，然后再开始代码发现与测试。
