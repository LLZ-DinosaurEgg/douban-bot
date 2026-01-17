# 快速开始指南

本项目提供两种运行方式：Docker 部署（推荐）和本地运行。

## 方式一：Docker 一键部署（推荐，最简单）

### 前置要求
- Docker 和 Docker Compose
- 不需要安装 Java 或 Maven

### 快速开始

1. **配置环境变量**

创建 `.env` 文件（必须至少配置 Cookie 和小组ID）：
```bash
cp env.example .env
```

编辑 `.env` 文件，至少配置以下内容：
```bash
# 豆瓣Cookie（必需）- 必须配置！
DOUBAN_COOKIE='your_douban_cookie_here'

# 爬虫配置（必需）- 至少配置一个小组
CRAWLER_GROUPS=beijingzufang
```

2. **启动服务**

```bash
# 使用部署脚本（推荐）
./deploy.sh start

# 或者直接使用 docker-compose
docker-compose up -d --build
```

3. **查看日志**

```bash
# 查看所有服务日志
./deploy.sh logs

# 或使用 docker-compose
docker-compose logs -f
```

4. **访问 Web 界面**

浏览器打开：`http://localhost:8080`

### 常用命令

```bash
# 启动服务
./deploy.sh start

# 停止服务
./deploy.sh stop

# 重启服务
./deploy.sh restart

# 查看状态
./deploy.sh status

# 查看日志
./deploy.sh logs web      # Web 服务日志
./deploy.sh logs crawler  # 爬虫服务日志
```

---

## 方式二：本地开发运行

### 前置要求

1. **安装 Java 17+**
   - macOS: `brew install openjdk@17`
   - Windows: 下载安装 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) 或 [OpenJDK](https://adoptium.net/)
   - Linux: `sudo apt install openjdk-17-jdk` (Ubuntu) 或 `sudo yum install java-17-openjdk` (CentOS)

2. **安装 Maven 3.9+**
   - macOS: `brew install maven`
   - Windows: 下载安装 [Maven](https://maven.apache.org/download.cgi)
   - Linux: `sudo apt install maven` (Ubuntu) 或 `sudo yum install maven` (CentOS)

### 快速开始

1. **验证安装**

```bash
java -version  # 应该显示 Java 17 或更高版本
mvn -version   # 应该显示 Maven 3.9 或更高版本
```

2. **配置环境变量**

创建 `.env` 文件或设置系统环境变量：
```bash
export DOUBAN_COOKIE='your_douban_cookie_here'
export CRAWLER_GROUPS='beijingzufang'
export CRAWLER_KEYWORDS='一居室,两居室'
export CRAWLER_EXCLUDE='中介,广告'
```

3. **构建项目**

```bash
# 下载依赖并构建
mvn clean package

# 如果遇到网络问题，可以使用国内 Maven 镜像
# 编辑 ~/.m2/settings.xml 或创建 pom.xml 同级的 settings.xml
```

4. **运行应用**

方式 A - 使用 Maven 运行（开发模式）：
```bash
mvn spring-boot:run
```

方式 B - 运行打包后的 JAR：
```bash
java -jar target/douban-bot-1.0.0.jar
```

5. **访问 Web 界面**

浏览器打开：`http://localhost:8080`

### 本地开发常用命令

```bash
# 清理并重新构建
mvn clean package

# 跳过测试构建
mvn clean package -DskipTests

# 运行应用
mvn spring-boot:run

# 查看依赖树
mvn dependency:tree

# 更新依赖
mvn dependency:resolve
```

---

## 配置说明

### 必需配置

1. **DOUBAN_COOKIE** - 豆瓣 Cookie（必需）
   - 获取方法：登录豆瓣 -> F12 开发者工具 -> Network -> 查看请求头中的 Cookie
   - 如果包含特殊字符，使用引号包裹

2. **CRAWLER_GROUPS** - 要爬取的小组ID（必需）
   - 多个用逗号分隔，如：`beijingzufang,shanghaizufang`

### 可选配置

3. **CRAWLER_KEYWORDS** - 关键词过滤
4. **CRAWLER_EXCLUDE** - 排除关键词
5. **CRAWLER_BOT** - 是否启用自动回复（需要配置 LLM_API_KEY）
6. **WEB_PORT** - Web 服务端口（默认 8080）

详细配置说明请查看 `env.example` 文件。

---

## 常见问题

### Q: Docker 构建失败？

A: 检查网络连接，可能需要配置 Docker 镜像加速器：
```bash
./setup-docker-mirror.sh
```

### Q: 本地运行提示找不到 Java？

A: 确保已安装 Java 17+，并配置 JAVA_HOME 环境变量。

### Q: Maven 下载依赖慢？

A: 配置国内 Maven 镜像，编辑 `~/.m2/settings.xml` 添加镜像源。

### Q: 应用启动成功但无法访问？

A: 检查端口是否被占用，或修改 `WEB_PORT` 环境变量。

### Q: 爬虫不工作？

A: 
1. 检查 `DOUBAN_COOKIE` 是否正确配置
2. 检查 `CRAWLER_GROUPS` 是否配置了有效的小组ID
3. 查看日志确认错误信息

---

## 下一步

- 查看 [README.md](README.md) 了解详细功能
- 配置自动回复功能需要设置 `LLM_API_KEY` 和 `CRAWLER_BOT=true`
- 通过 Web 界面 `http://localhost:8080` 查看爬取的数据
