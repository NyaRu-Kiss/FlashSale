# 本地开发

主机不要求安装 Java。项目目标使用 Java 21，开发构建可在 Docker 中执行：

```powershell
docker build -f Dockerfile.dev -t flashsale-dev .
docker run --rm -v "${PWD}:/workspace" -v "flashsale-m2:/root/.m2" flashsale-dev
```

后续基础设施统一由 Docker Compose 启动；服务代码可挂载到同一工作目录进行开发。
