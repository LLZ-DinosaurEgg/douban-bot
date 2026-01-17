# 🥚 恐龙蛋的豆瓣金枕榴莲机器人

<div align="center">

![恐龙蛋](https://img.shields.io/badge/作者-恐龙蛋-ff6b9d?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge)

</div>

---

一个基于 Java Spring Boot 开发的豆瓣小组数据采集和智能回复系统，由 🦕🥚 **恐龙蛋** 制作，支持自动采集小组帖子、评论，并集成大模型智能回复功能。

## 🥚 项目简介

这是 **恐龙蛋** 制作的一个功能完整的豆瓣小组数据采集和自动回复系统，主要功能包括：

- **智能数据采集**：自动采集豆瓣小组的帖子和评论数据
- **关键词过滤**：支持关键词匹配和排除，精准筛选目标内容
- **数据存储**：使用 SQLite 数据库持久化存储采集的数据
<img width="2310" height="1324" alt="fc08fdee-31ba-4959-9b1e-7fe0941a61e8" src="https://github.com/user-attachments/assets/6cb7d958-d6fe-433a-b24c-3594811542ae" />

- **风格学习**：分析小组历史帖子和评论，学习回复风格
<img width="2404" height="1194" alt="4c8b1f31-1da7-48dd-a08d-950a1a96b56d" src="https://github.com/user-attachments/assets/a377d16e-e519-4b70-80b1-ae42d639ec3d" />

- **智能回复**：基于大模型 API，生成符合小组风格的自动回复
<img width="1832" height="1246" alt="image" src="https://github.com/user-attachments/assets/3f9d0efe-123c-4dea-a32d-7c4c3f9843c9" />
<img width="2354" height="1422" alt="image" src="https://github.com/user-attachments/assets/8829fbc1-c746-43c9-80b8-f7272773a3e9" />

- **Web 管理界面**：现代化的 Web 界面，实时查看采集的数据

## ✨ 功能特性

### 数据采集功能
- ✅ 自动采集小组帖子列表和详情
- ✅ 自动采集帖子下的所有评论
- ✅ 支持多小组同时采集
- ✅ 支持关键词匹配和排除
- ✅ 自动去重，避免重复存储
- ✅ 随机延迟，降低被封风险
- ✅ 定时任务自动执行

### Web 管理界面
- ✅ 现代化的 Web 界面，美观易用
- ✅ 实时统计信息展示（小组数、帖子数、评论数）
- ✅ 小组列表浏览和搜索功能
- ✅ 帖子列表分页展示
- ✅ 帖子详情查看，包括完整内容和评论
- ✅ 关键词标签高亮显示
- ✅ 匹配帖子筛选功能
- ✅ 响应式设计，支持各种屏幕尺寸

### 自动回复机器人
- ✅ 基于大模型生成智能回复
- ✅ 学习小组历史回复风格
- ✅ 自动匹配需要回复的帖子
- ✅ 可配置回复关键词和延迟时间
- ✅ 生成符合小组风格的个性化回复

## 🛠️ 技术栈

- **后端框架**: Spring Boot 3.2.0
- **编程语言**: Java 17+
- **数据库**: SQLite3
- **数据库访问**: JDBI3
- **HTML解析**: Jsoup
- **HTTP客户端**: Apache HttpClient 5
- **Web服务器**: Spring Boot 内嵌 Tomcat
- **前端**: 原生 HTML/CSS/JavaScript（无框架依赖）
- **大模型API**: 支持 OpenAI 兼容的 API
- **构建工具**: Maven 3.9+
- **定时任务**: Spring Scheduler

## 📦 安装部署

### 方式一：Docker 一键部署（推荐）

#### 前置要求

- Docker 和 Docker Compose
- 有效的豆瓣账号 Cookie（用于数据采集）
- 大模型 API 密钥（用于自动回复功能，可选）

#### 快速开始

1. **克隆项目**
```bash
git clone https://github.com/your-username/douban-bot.git
cd douban-bot
```

2. **配置环境变量**

创建 `.env` 文件（可参考 `env.example`）：
```bash
cp env.example .env
# 然后编辑 .env 文件，至少配置 DOUBAN_COOKIE 和 CRAWLER_GROUPS
```

或者手动创建 `.env` 文件：
```bash
# 豆瓣Cookie（必需）
# 注意：如果 Cookie 包含分号、空格等特殊字符，请使用引号包裹（单引号或双引号都可以）
DOUBAN_COOKIE='your_douban_cookie_here'
# 或者
DOUBAN_COOKIE="your_douban_cookie_here"

# 大模型API配置（可选，自动回复功能需要）
LLM_API_KEY=your_api_key_here
LLM_API_BASE=https://api.openai.com/v1
LLM_MODEL=gpt-3.5-turbo
LLM_TEMPERATURE=0.7
LLM_MAX_TOKENS=500

# 数据采集配置
CRAWLER_GROUPS=beijingzufang,shanghaizufang
CRAWLER_KEYWORDS=一居室,两居室,合租
CRAWLER_EXCLUDE=中介,广告
CRAWLER_PAGES=10
CRAWLER_SLEEP=900
CRAWLER_BOT=false
CRAWLER_REPLY_KEYWORDS=
CRAWLER_MIN_REPLY_DELAY=30
CRAWLER_MAX_REPLY_DELAY=300
CRAWLER_MAX_HISTORY_POSTS=50
CRAWLER_MAX_HISTORY_COMMENTS=200
CRAWLER_DEBUG=false

# Web服务端口（可选，默认8080）
WEB_PORT=8080
```

3. **一键启动服务**

使用部署脚本（推荐）：
```bash
# 启动所有服务（会自动检查配置、创建目录、构建镜像）
./deploy.sh start

# 或者直接使用 docker-compose
docker-compose up -d --build
```

4. **访问 Web 界面**

浏览器访问：`http://localhost:8080`（或你配置的端口）

#### 部署脚本使用说明

项目提供了便捷的部署脚本 `deploy.sh`，支持以下命令：

```bash
# 启动服务（默认命令）
./deploy.sh start

# 停止服务
./deploy.sh stop

# 重启服务
./deploy.sh restart

# 查看日志（所有服务）
./deploy.sh logs

# 查看指定服务日志
./deploy.sh logs web      # 查看 Web 服务日志
./deploy.sh logs crawler  # 查看数据采集服务日志

# 查看服务状态
./deploy.sh status
```

部署脚本会自动：
- ✅ 检查 Docker 和 Docker Compose 是否安装
- ✅ 检查 `.env` 文件是否存在（不存在会从 `env.example` 创建）
- ✅ 验证必要的配置项（如 `DOUBAN_COOKIE`）
- ✅ 创建必要的目录（如 `data` 目录）
- ✅ 构建并启动所有服务

#### Docker 部署说明

- **数据持久化**：数据库文件存储在 `./data` 目录，容器重启后数据不会丢失
- **服务分离**：
  - `web` 服务：提供 Web 管理界面（默认端口 8080，可通过 `WEB_PORT` 环境变量修改）
  - `crawler` 服务：运行数据采集任务（定时自动执行）
- **环境变量配置**：所有配置通过 `.env` 文件设置，无需修改代码
- **健康检查**：Web 服务包含健康检查，确保服务正常运行
- **自动重启**：服务异常退出时会自动重启

#### 网络问题排查

如果构建镜像时遇到网络超时错误（如 `failed to fetch oauth token`），请配置 Docker 镜像加速器：

**快速配置（推荐）**：
```bash
# 使用提供的配置脚本
./setup-docker-mirror.sh
```

**手动配置**：
1. 编辑 `~/.docker/daemon.json`（macOS/Linux）或 Docker Desktop 设置中的 Docker Engine（Windows）
2. 添加镜像加速器配置（详见 `DOCKER_MIRROR_SETUP.md`）
3. 重启 Docker 服务

**常用镜像加速器**：
- DaoCloud: `https://docker.m.daocloud.io`
- DockerProxy: `https://dockerproxy.com`
- 中科大: `https://docker.mirrors.ustc.edu.cn`

### 方式二：本地开发部署

#### 前置要求

- Java 17 或更高版本
- Maven 3.9 或更高版本
- 有效的豆瓣账号 Cookie（用于数据采集）
- 大模型 API 密钥（用于自动回复功能，可选）

#### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/your-username/douban-bot.git
cd douban-bot
```

2. **安装依赖**
```bash
mvn clean install
```

3. **配置环境变量**

创建 `.env` 文件或设置系统环境变量：
```bash
export DOUBAN_COOKIE='your_douban_cookie_here'
export CRAWLER_GROUPS='beijingzufang'
export CRAWLER_KEYWORDS='一居室,两居室'
export CRAWLER_BOT=true
```

或者编辑 `src/main/resources/application.yml` 文件。

4. **运行应用**

```bash
# 使用 Maven 运行
mvn spring-boot:run

# 或者先打包再运行
mvn clean package
java -jar target/douban-bot-1.0.0.jar
```

5. **访问 Web 界面**

浏览器访问：`http://localhost:8080`

## 🚀 使用方法

### Web 管理界面

启动应用后，通过浏览器管理爬取的数据：

- 📊 实时统计信息（小组数、帖子数、评论数）
- 📋 小组列表浏览和搜索
- 📝 帖子列表查看，支持分页
- 🔍 帖子详情查看，包括评论
- 🏷️ 关键词标签显示
- ✅ 匹配帖子筛选

### 爬虫配置

爬虫通过配置文件或环境变量进行配置：

**配置文件方式**（`application.yml`）：
```yaml
app:
  crawler-groups: beijingzufang,shanghaizufang
  crawler-keywords: 一居室,两居室,合租
  crawler-exclude: 中介,广告
  crawler-pages: 10
  crawler-sleep: 900
  crawler-bot: false
```

**环境变量方式**：
```bash
export CRAWLER_GROUPS='beijingzufang,shanghaizufang'
export CRAWLER_KEYWORDS='一居室,两居室,合租'
export CRAWLER_EXCLUDE='中介,广告'
export CRAWLER_PAGES=10
export CRAWLER_SLEEP=900
export CRAWLER_BOT=false
```

### 自动回复配置

启用自动回复机器人需要配置：

```yaml
app:
  crawler-bot: true
  llm-api-key: your_api_key_here
  crawler-reply-keywords: 招聘,求职
  crawler-min-reply-delay: 30
  crawler-max-reply-delay: 300
```

## ⚙️ 配置说明

### 环境变量配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `DOUBAN_COOKIE` | 豆瓣 Cookie（必需） | - |
| `CRAWLER_GROUPS` | 小组ID，多个用逗号分隔 | - |
| `CRAWLER_KEYWORDS` | 关键词，多个用逗号分隔 | - |
| `CRAWLER_EXCLUDE` | 排除关键词，多个用逗号分隔 | - |
| `CRAWLER_PAGES` | 爬取页数 | 10 |
| `CRAWLER_SLEEP` | 睡眠时长（秒） | 900 |
| `CRAWLER_BOT` | 是否启用自动回复机器人 | false |
| `CRAWLER_REPLY_KEYWORDS` | 需要回复的关键词 | - |
| `LLM_API_KEY` | 大模型 API 密钥 | - |
| `LLM_API_BASE` | API 基础 URL | https://api.openai.com/v1 |
| `LLM_MODEL` | 模型名称 | gpt-3.5-turbo |
| `WEB_PORT` | Web 服务端口 | 8080 |
| `DB_PATH` | 数据库文件路径 | ./db.sqlite3 |

### 数据库

数据默认存储在 `./db.sqlite3`（或配置的路径），包含以下表：

- **Group**: 小组信息
- **Post**: 帖子信息
- **Comment**: 评论信息

数据库会在应用首次启动时自动创建。

## 📁 项目结构

```
douban-bot/
├── src/main/java/com/douban/bot/
│   ├── DoubanBotApplication.java    # Spring Boot 应用入口
│   ├── config/                      # 配置类
│   │   ├── AppConfig.java
│   │   └── DatabaseConfig.java
│   ├── controller/                  # Web 控制器
│   │   ├── ApiController.java       # REST API
│   │   └── WebController.java       # 静态文件服务
│   ├── db/                          # 数据访问层
│   │   ├── GroupDao.java
│   │   ├── PostDao.java
│   │   ├── CommentDao.java
│   │   └── RepositoryService.java
│   ├── model/                       # 数据模型
│   │   ├── Group.java
│   │   ├── Post.java
│   │   └── Comment.java
│   ├── service/                     # 业务服务
│   │   ├── CrawlerService.java      # 爬虫服务
│   │   ├── CrawlerScheduler.java    # 定时任务
│   │   ├── HtmlParser.java          # HTML 解析
│   │   ├── LlmClient.java           # LLM 客户端
│   │   └── ReplyBotService.java     # 自动回复
│   └── utils/                       # 工具类
│       └── HttpUtils.java
├── src/main/resources/
│   ├── application.yml              # Spring Boot 配置
│   └── static/                      # 前端静态文件
├── pom.xml                          # Maven 配置
├── Dockerfile.java                  # Docker 构建文件
├── docker-compose.yml               # Docker Compose 配置
├── deploy.sh                        # 部署脚本
└── README.md
```

## 🔧 工作原理

### 爬虫流程

1. 定时任务触发爬虫（默认每 15 分钟）
2. 根据小组ID和关键词配置，爬取帖子列表
3. 对每个帖子，爬取详细内容和评论
4. 根据关键词和排除词过滤帖子
5. 将数据存储到 SQLite 数据库
6. 如果启用自动回复，处理需要回复的帖子

### Web 服务器流程

1. **启动服务器**：Spring Boot 内嵌 Tomcat 监听指定端口
2. **API 路由**：提供 RESTful API 接口
   - `GET /api/groups` - 获取所有小组
   - `GET /api/posts?group_id=xxx&page=1&page_size=20` - 获取帖子列表
   - `GET /api/post/{postId}` - 获取帖子详情
   - `GET /api/comments/{postId}` - 获取评论列表
   - `GET /api/stats` - 获取统计信息
3. **前端交互**：通过 JavaScript 调用 API，动态渲染数据
4. **静态文件服务**：提供 HTML、CSS、JS 等静态资源

### 自动回复流程

1. **风格学习**：分析小组历史帖子和评论，提取：
   - 常用词汇和短语
   - 平均回复长度
   - 回复模式（语气、风格等）

2. **生成系统提示词**：基于学习到的风格特征，构建大模型系统提示词

3. **匹配帖子**：根据配置的关键词和规则，筛选需要回复的帖子

4. **生成回复**：调用大模型 API，生成符合小组风格的回复

5. **延迟发送**：随机延迟后记录回复（实际发送需要额外实现）

## ⚠️ 注意事项

1. **Cookie 安全**：
   - 不要将 Cookie 提交到公共仓库
   - 建议使用环境变量或配置文件（不纳入版本控制）

2. **爬取频率**：
   - 建议设置合理的 `CRAWLER_SLEEP` 参数，避免请求过于频繁
   - 默认 900 秒（15分钟）循环一次

3. **大模型 API**：
   - 需要有效的 API 密钥
   - 注意 API 调用费用
   - 建议先测试生成效果，确认质量后再批量使用

4. **自动回复**：
   - 当前版本只生成回复内容，不会自动发送到豆瓣
   - 实际发送需要实现豆瓣的 API 调用（需要登录和权限）
   - 建议先人工审核生成的回复质量

5. **法律合规**：
   - 请遵守豆瓣的使用条款
   - 不要用于商业用途或恶意行为
   - 尊重其他用户的权益

## 🔍 常见问题

### Q: 如何配置包含特殊字符的 Cookie？

A: 如果 Cookie 中包含分号、空格等特殊字符，请在 `.env` 文件中使用引号包裹：
```bash
# 使用单引号（推荐）
DOUBAN_COOKIE='your_cookie_with_semicolons;and_spaces here'

# 或使用双引号
DOUBAN_COOKIE="your_cookie_with_semicolons;and_spaces here"
```

### Q: 如何获取豆瓣 Cookie？

A: 
1. 登录豆瓣网站
2. 打开浏览器开发者工具（F12）
3. 在 Network 标签页中，找到任意请求
4. 查看 Request Headers 中的 Cookie 字段
5. 复制完整的 Cookie 值

### Q: 支持哪些大模型 API？

A: 目前支持 OpenAI 兼容的 API，包括：
- OpenAI GPT 系列
- 其他兼容 OpenAI API 格式的服务

### Q: 数据库文件在哪里？

A: 默认在项目根目录下的 `db.sqlite3` 文件，或通过 `DB_PATH` 环境变量配置的路径。

### Q: 如何修改爬虫执行频率？

A: 通过 `CRAWLER_SLEEP` 环境变量或 `app.crawler-sleep` 配置项设置，单位为秒。

### Q: 如何启用自动回复功能？

A: 设置 `CRAWLER_BOT=true` 并配置 `LLM_API_KEY` 环境变量即可。

## 🥚 关于作者

**恐龙蛋** 🥚 

一个喜欢编程、喜欢美女的开发者 💕

如果你喜欢这个项目，欢迎给个 ⭐ Star！

## 📝 开发计划

- [ ] 支持更多大模型 API（Claude、本地模型等）
- [ ] 实现实际的豆瓣回复发送功能
- [ ] 添加数据导出功能
- [ ] 支持更多数据可视化
- [ ] 添加用户认证功能
- [ ] 更多可爱的界面元素 🎨

## 📄 许可证

本项目仅供学习和研究使用，请遵守相关法律法规和平台使用条款。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

特别感谢所有为这个项目贡献的朋友们 💕

## 📮 联系方式

如有问题或建议，请通过 Issue 反馈。

---

<div align="center">

**Made with 💕 by 恐龙蛋 🥚**

</div>
