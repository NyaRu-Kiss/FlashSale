# 本地开发

主机不要求安装 Java。项目目标使用 Java 21，Maven 统一在 Docker 中执行，开发和验收环境共享命名卷 `flashsale-m2` 作为 Maven 本地仓库：

```powershell
docker build -f Dockerfile.dev -t flashsale-dev .
docker run --rm -v "${PWD}:/workspace" -v "flashsale-m2:/root/.m2" flashsale-dev
```

Docker Compose 中执行 Maven 的迁移服务和验收服务也统一挂载 `flashsale-m2:/root/.m2`。主机的 `~/.m2` 不作为本项目依赖缓存来源，避免主机与容器之间产生不一致。

后续基础设施统一由 Docker Compose 启动；服务代码可挂载到同一工作目录进行开发。CI 可以使用独立的缓存，不需要与本地 Docker 卷共享。
