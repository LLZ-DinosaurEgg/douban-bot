# Docker 镜像加速器配置指南

如果构建 Docker 镜像时遇到网络超时问题，请配置国内镜像加速器。

## 方法一：配置 Docker 镜像加速器（推荐）

### macOS / Linux

1. 创建或编辑 Docker 配置文件：
```bash
mkdir -p ~/.docker
```

2. 编辑配置文件 `~/.docker/daemon.json`，添加以下内容：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.com",
    "https://docker.nju.edu.cn",
    "https://docker.mirrors.sjtug.sjtu.edu.cn"
  ],
  "builder": {
    "gc": {
      "defaultKeepStorage": "20GB",
      "enabled": true
    }
  },
  "experimental": false
}
```

3. 重启 Docker Desktop（macOS）或 Docker 服务（Linux）：
```bash
# macOS: 重启 Docker Desktop 应用
# Linux:
sudo systemctl restart docker
```

### Windows

1. 右键点击系统托盘中的 Docker 图标
2. 选择 "Settings" -> "Docker Engine"
3. 在 JSON 配置中添加 `registry-mirrors` 配置（同上）
4. 点击 "Apply & Restart"

## 方法二：使用环境变量（临时方案）

构建时使用国内镜像：

```bash
# 使用环境变量指定镜像源
export DOCKER_BUILDKIT=1
docker-compose build --build-arg ALPINE_MIRROR=mirrors.aliyun.com
```

## 方法三：修改 Dockerfile 使用国内镜像源

如果上述方法都不行，可以修改 Dockerfile 直接使用国内镜像：

1. 将 `golang:1.24-alpine` 改为从国内镜像拉取
2. 在 Dockerfile 中添加镜像源配置

## 常见镜像加速器地址

- 中科大镜像：https://docker.mirrors.ustc.edu.cn
- 网易镜像：https://hub-mirror.c.163.com
- 阿里云镜像：https://your-id.mirror.aliyuncs.com（需要注册阿里云账号获取专属地址）
- DaoCloud 镜像：https://docker.m.daocloud.io
- DockerProxy：https://dockerproxy.com

## 验证配置

配置完成后，可以通过以下命令验证：

```bash
docker info | grep -A 10 "Registry Mirrors"
```

应该能看到你配置的镜像地址。
