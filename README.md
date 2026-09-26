# FlashSale

FlashSale 是一个基于 Java 21、Spring Boot 和 Spring Cloud 的高并发限时抢购系统示例。项目覆盖用户认证、商品、活动、库存、优惠券、订单、支付、异步消息和后台调度等业务链路，重点验证高并发入口下的库存安全、幂等处理和最终一致性。

## 项目简介

系统采用 Docker Compose 部署，使用 PostgreSQL 保存最终业务状态，Redis 承担高并发预扣和限购，RocketMQ 处理异步事件，Nacos 提供服务发现与配置，XXL-Job 执行 Outbox 投递、预热、恢复和对账任务。Gateway 负责统一入口、鉴权和限流，业务服务继续执行 JWT、角色和资源归属校验。

核心业务流程：

```text
请求 → Gateway 鉴权/限流 → Redis Lua 原子预扣
     → PostgreSQL 本地事务写业务记录和 Outbox
     → XXL-Job 投递 Outbox → RocketMQ
     → 消费幂等处理 → PostgreSQL/Redis 对账与补偿
```

## 项目目录

| 目录 | 说明 |
|---|---|
| `flashsale-gateway` | API 网关、路由、鉴权入口和限流 |
| `flashsale-auth` | 注册、登录、JWT 和账户状态 |
| `flashsale-product` | 商品管理、上下架和商品查询缓存 |
| `flashsale-activity` | 限时活动、预热、库存门闸、暂停和恢复 |
| `flashsale-inventory` | 普通库存和活动库存预占、确认、释放 |
| `flashsale-coupon` | 优惠券模板、领券、限领和消费 |
| `flashsale-order` | 订单创建、幂等、取消和超时处理 |
| `flashsale-payment` | 支付、回调、支付幂等和履约确认 |
| `flashsale-job` | XXL-Job 对账、补偿和恢复任务 |
| `flashsale-common` | 认证、Trace、Outbox、消息和通用基础设施 |
| `flashsale-migration` | Flyway 数据库迁移 |
| `db` | PostgreSQL/XXL-Job 初始化脚本和迁移文件 |
| `config` | Nacos、Prometheus、Loki、Grafana 等配置 |
| `load` | H03 k6 压测脚本、场景数据准备和对账 SQL |
| `docs` | 需求、设计、验收记录和测试证据 |
| `.codex/skills` | FlashSale 验收和设计偏离处理 Skill |

## 环境要求

- Docker Engine 和 Docker Compose v2
- Java 21（本地 Maven 开发或测试需要）
- Maven 3.9+
- `curl`、`jq`（H03 数据准备脚本需要）

Docker Compose 中的 Maven 服务统一使用 `flashsale-m2:/root/.m2`，不会把主机 `~/.m2` 作为项目依赖缓存来源。

## 如何启动

### 启动基础环境和业务服务

```bash
docker compose --profile r21 up -d \
  postgres redis nacos migration xxl-mysql xxl-job-admin \
  rocketmq-namesrv rocketmq-broker rocketmq-init \
  auth gateway product activity inventory coupon order payment job
```

查看状态和日志：

```bash
docker compose ps
docker compose logs -f gateway
```

Gateway 默认地址为 `http://127.0.0.1:8080`。默认管理员账号由环境变量控制，默认值为 `admin` / `local-admin-password`。本地配置可复制 `.env.example` 为 `.env` 后按需修改。

基础 Compose 默认关闭 Outbox 投递和消费者。要验收完整异步链路，使用下面的 H03 独立环境；该环境会启用 Outbox、消费者和 XXL-Job，并初始化六个 `outboxDispatch` 周期任务。

### 启动接口验收环境

```bash
docker compose --env-file .env.acceptance \
  -f docker-compose.yml -f docker-compose.acceptance.yml \
  --profile r21 up -d
```

验收完成后停止服务（不删除卷）：

```bash
docker compose --env-file .env.acceptance \
  -f docker-compose.yml -f docker-compose.acceptance.yml \
  --profile r21 down
```

### 停止本地服务

```bash
docker compose --profile r21 down
```

命令默认保留 Docker 卷；确认数据不再需要后再使用 `down --volumes` 清理临时数据。

## 测试与验收

常规 Maven 测试：

```bash
mvn -B test
```

H03 使用独立 Compose project 和临时数据卷：

```bash
H03_SCENARIO=activity_burst load/h03/run.sh up
H03_SCENARIO=activity_burst load/h03/run.sh prepare
H03_USER_COUNT=9000 LOAD_TOKENS_FILE=load/h03/tokens.txt load/h03/prepare-users.sh
H03_SCENARIO=activity_burst LOAD_TOKENS_FILE=load/h03/tokens.txt load/h03/run.sh run
load/h03/run.sh verify
load/h03/run.sh down
```

H03 测试记录、k6 原始输出、容器日志和 SHA-256 校验文件见：[docs/h03-acceptance](docs/h03-acceptance/)。

## 项目亮点

### 高并发下的库存安全

活动库存通过 Redis Lua 脚本原子预扣，数据库库存和库存流水作为最终账本，避免并发请求造成负库存或超卖。

### Outbox 与消息最终一致性

业务写入和 Outbox 在同一个 PostgreSQL 本地事务中提交。XXL-Job 以每秒周期触发 `outboxDispatch`，RocketMQ 消费端使用独立幂等记录，失败消息可重试并通过对账任务发现积压。

### 完整幂等保护

订单、领券、支付、回调和消息消费均使用业务幂等键、唯一约束或状态 CAS。相同请求复用结果，不同请求复用同一键时返回冲突，重复消息不会重复修改业务数据。

### 活动状态和恢复屏障

活动开始前先预热详情和库存；暂停先关闭 Redis 新预扣门闸，再截取事件屏障；恢复前检查 Outbox、事件序号、库存 checkpoint、库存流水和 Redis 状态。

### 可观测性

请求和异步事件统一透传 Trace ID，结构化日志包含用户、订单、事件、幂等和 Outbox 字段；Prometheus、Loki、Grafana、SkyWalking 和 Alertmanager 配置位于 `config/`。

### H03 验收证据

在 `500 QPS × 20 秒` 场景下，系统稳定处理了约 10,000 条请求，未出现超卖、重复有效订单或核心数据不一致。

在 `1000 QPS × 10 秒` 场景下，受本机压测能力限制，实际完成 7,738 条请求，未达到完整 10,000 条；已处理请求仍未出现超卖或核心数据不一致。

这些结果对应的 k6 原始输出和容器日志已保存在 `docs/h03-acceptance/evidence/`。

## 设计文档

- [API 设计](docs/api-design.md)
- [关键业务时序](docs/sequence-design.md)
- [跨服务可靠性与可观测性](docs/cross-cutting-design.md)
- [数据库设计](docs/database-design.md)
- [验收任务状态](docs/task.md)
- [H03 测试记录与证据](docs/h03-acceptance/README.md)

## 说明

H03 记录中的 1 秒 10,000 请求是目标突发窗口；当前机器未完成该窗口的实测，因此项目文档将其作为基于已验证不变量和系统设计的理论容量判断，不作为本机已经达到的容量基线。
