# Docker 镜像加速器问题修复

如果遇到 401 Unauthorized 错误，可以尝试以下方法：

## 方法 1：更换镜像加速器

编辑 `~/.docker/daemon.json`，使用以下可用的镜像加速器：

```json
{
  "registry-mirrors": [
    "https://dockerproxy.com",
    "https://docker.nju.edu.cn",
    "https://docker.mirrors.sjtug.sjtu.edu.cn"
  ]
}
```

然后重启 Docker Desktop。

## 方法 2：临时禁用镜像加速器

编辑 `~/.docker/daemon.json`，移除或注释掉 `registry-mirrors`：

```json
{
  "builder": {
    "gc": {
      "defaultKeepStorage": "20GB",
      "enabled": true
    }
  }
}
```

然后重启 Docker Desktop。

## 方法 3：使用 Docker BuildKit

设置环境变量后构建：

```bash
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
docker-compose up -d --build
```

## 验证修复

```bash
# 重新构建
docker-compose up -d --build

# 查看构建日志
docker-compose logs -f
```
