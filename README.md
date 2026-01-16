# 豆瓣小组爬虫与自动回复机器人

一个基于 Go 语言开发的豆瓣小组爬虫工具，支持自动爬取小组帖子、评论，并集成大模型智能回复功能。

## 📋 项目简介

本项目是一个功能完整的豆瓣小组数据采集和自动回复系统，主要功能包括：

- **智能爬虫**：自动爬取豆瓣小组的帖子和评论数据
- **关键词过滤**：支持关键词匹配和排除，精准筛选目标内容
- **数据存储**：使用 SQLite 数据库持久化存储爬取的数据
- **风格学习**：分析小组历史帖子和评论，学习回复风格
- **智能回复**：基于大模型 API，生成符合小组风格的自动回复

## ✨ 功能特性

### 爬虫功能
- ✅ 自动爬取小组帖子列表和详情
- ✅ 自动爬取帖子下的所有评论
- ✅ 支持多小组同时爬取
- ✅ 支持关键词匹配和排除
- ✅ 自动去重，避免重复存储
- ✅ 随机延迟，降低被封风险

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

- **后端语言**: Go 1.24+
- **数据库**: SQLite3
- **HTML解析**: goquery
- **HTTP客户端**: net/http
- **Web服务器**: Go 标准库 net/http
- **前端**: 原生 HTML/CSS/JavaScript（无框架依赖）
- **大模型API**: 支持 OpenAI 兼容的 API

## 📦 安装

### 前置要求

- Go 1.24 或更高版本
- 有效的豆瓣账号 Cookie（用于爬取）
- 大模型 API 密钥（用于自动回复功能，可选）

### 安装步骤

1. 克隆项目
```bash
git clone https://github.com/LLZ-DinosaurEgg/douban-crawler.git
cd douban-crawler
```

2. 安装依赖
```bash
go mod download
```

3. 配置 Cookie
   - 登录豆瓣网站
   - 获取浏览器 Cookie
   - 在 `config/config.go` 中设置 `COOKIE` 变量

4. 配置大模型 API（如使用自动回复功能）
   - 在 `config/config.go` 中设置 `LLM_API_KEY`
   - 可选：修改 `LLM_API_BASE`、`LLM_MODEL` 等配置

## 🚀 使用方法

### Web 管理界面（推荐）

启动 Web 服务器，通过浏览器管理爬取的数据：

```bash
go run cmd/web.go
```

然后在浏览器中访问：`http://localhost:8080`

**Web 界面功能：**
- 📊 实时统计信息（小组数、帖子数、评论数）
- 📋 小组列表浏览和搜索
- 📝 帖子列表查看，支持分页
- 🔍 帖子详情查看，包括评论
- 🏷️ 关键词标签显示
- ✅ 匹配帖子筛选

**Web 服务器参数：**
- `-port`: Web服务器端口（默认：8080）
- `-db`: 数据库文件路径（默认：./db.sqlite3）

示例：
```bash
go run cmd/web.go -port 8080 -db ./db.sqlite3
```

### 基础爬虫模式

只爬取数据，不启用自动回复：

```bash
go run cmd/main.go -g "小组ID1,小组ID2" -k "关键词1,关键词2" -pages 10
```

### 自动回复模式

启用自动回复机器人：

```bash
go run cmd/main.go \
  -g "小组ID" \
  -k "关键词1,关键词2" \
  -bot \
  -reply-keywords "回复关键词1,回复关键词2" \
  -min-reply-delay 30 \
  -max-reply-delay 300
```

### 命令行参数说明

| 参数 | 简写 | 说明 | 默认值 |
|------|------|------|--------|
| `-g` | - | 小组ID，多个用逗号分隔 | - |
| `-k` | - | 关键词，多个用逗号分隔 | - |
| `-e` | - | 排除关键词，多个用逗号分隔 | - |
| `-pages` | - | 爬取页数 | 10 |
| `-sleep` | - | 睡眠时长（秒） | 900 |
| `-v` | - | 调试模式 | false |
| `-bot` | - | 启用自动回复机器人 | false |
| `-reply-keywords` | - | 需要回复的关键词（为空则回复所有匹配的帖子） | - |
| `-min-reply-delay` | - | 最小回复延迟（秒） | 30 |
| `-max-reply-delay` | - | 最大回复延迟（秒） | 300 |
| `-max-history-posts` | - | 用于学习风格的最大历史帖子数 | 50 |
| `-max-history-comments` | - | 用于学习风格的最大历史评论数 | 200 |

### 使用示例

#### 示例1：启动 Web 管理界面

```bash
# 使用默认端口 8080
go run cmd/web.go

# 指定端口和数据库路径
go run cmd/web.go -port 9000 -db ./my_database.sqlite3
```

然后在浏览器访问 `http://localhost:8080` 查看数据。

#### 示例2：爬取租房小组的房源信息

```bash
go run cmd/main.go \
  -g "beijingzufang" \
  -k "一居室,两居室,合租" \
  -e "中介,广告" \
  -pages 20 \
  -sleep 1800
```

爬取完成后，可以启动 Web 服务器查看数据：
```bash
go run cmd/web.go
```

#### 示例3：启用自动回复，回复招聘相关帖子

```bash
go run cmd/main.go \
  -g "job" \
  -k "招聘,求职,工作" \
  -bot \
  -reply-keywords "招聘,岗位" \
  -min-reply-delay 60 \
  -max-reply-delay 600
```

#### 示例4：调试模式运行

```bash
go run cmd/main.go \
  -g "test" \
  -k "测试" \
  -v \
  -pages 5
```

## ⚙️ 配置说明

### 基础配置

在 `config/config.go` 中修改以下配置：

```go
// 豆瓣 Cookie（必需）
COOKIE = "your_douban_cookie_here"

// 大模型 API 配置（自动回复功能需要）
LLM_API_KEY = "your_api_key_here"
LLM_API_BASE = "https://api.openai.com/v1"
LLM_MODEL = "gpt-3.5-turbo"
LLM_TEMPERATURE = 0.7
LLM_MAX_TOKENS = 500
```

### 数据库

数据默认存储在 `./db.sqlite3`，包含以下表：

- **Group**: 小组信息
- **Post**: 帖子信息
- **Comment**: 评论信息

## 📁 项目结构

```
douban-crawler/
├── cmd/
│   ├── main.go              # 爬虫主程序入口
│   └── web.go               # Web服务器入口
├── config/
│   └── config.go            # 配置文件
├── web/                      # Web前端文件
│   ├── index.html           # 前端主页面
│   └── static/              # 静态资源
│       ├── style.css        # 样式文件
│       └── app.js           # JavaScript逻辑
├── internal/
│   ├── bot/                 # 自动回复机器人
│   │   ├── reply_bot.go     # 回复机器人核心逻辑
│   │   └── style_learner.go # 风格学习器
│   ├── crawler/             # 爬虫模块
│   │   ├── crawler.go       # 爬虫核心逻辑
│   │   └── parser.go        # HTML解析器
│   ├── db/                  # 数据库模块
│   │   ├── model/           # 数据模型
│   │   │   ├── group.go
│   │   │   ├── post.go
│   │   │   └── comment.go
│   │   └── sqlite/          # SQLite操作
│   │       └── sqlite.go
│   ├── llm/                 # 大模型客户端
│   │   └── client.go
│   ├── server/              # Web服务器
│   │   └── server.go        # HTTP服务器和API路由
│   └── utils/               # 工具函数
│       └── http.go
├── go.mod
└── README.md
```

## 🔧 工作原理

### 爬虫流程

1. 根据小组ID和关键词配置，爬取帖子列表
2. 对每个帖子，爬取详细内容和评论
3. 根据关键词和排除词过滤帖子
4. 将数据存储到 SQLite 数据库
5. 循环执行，定期更新数据

### Web 服务器流程

1. **启动服务器**：监听指定端口，提供 HTTP 服务
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
   - 回复模式（疑问句、感叹句、表情符号使用等）

2. **生成系统提示词**：基于学习到的风格特征，构建大模型系统提示词

3. **匹配帖子**：根据配置的关键词和规则，筛选需要回复的帖子

4. **生成回复**：调用大模型 API，生成符合小组风格的回复

5. **延迟发送**：随机延迟后记录回复（实际发送需要额外实现）

## ⚠️ 注意事项

1. **Cookie 安全**：
   - 不要将 Cookie 提交到公共仓库
   - 建议使用环境变量或配置文件（不纳入版本控制）

2. **爬取频率**：
   - 建议设置合理的 `-sleep` 参数，避免请求过于频繁
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

### Q: 如何修改回复风格？

A: 可以通过调整以下参数：
- `-max-history-posts`: 增加历史帖子数量，学习更多风格
- `-max-history-comments`: 增加历史评论数量
- 修改 `config/config.go` 中的 `LLM_TEMPERATURE` 参数

### Q: 数据库文件在哪里？

A: 默认在项目根目录下的 `db.sqlite3` 文件。

### Q: 如何使用 Web 管理界面？

A: 
1. 先运行爬虫程序爬取数据（`go run cmd/main.go`）
2. 启动 Web 服务器（`go run cmd/web.go`）
3. 在浏览器中访问 `http://localhost:8080`
4. 在界面中浏览小组、查看帖子和评论

### Q: Web 服务器和爬虫可以同时运行吗？

A: 可以！Web 服务器只读取数据库，不会影响爬虫的运行。你可以：
- 在一个终端运行爬虫：`go run cmd/main.go -g "xxx" -k "xxx"`
- 在另一个终端运行 Web 服务器：`go run cmd/web.go`
- 爬虫会持续更新数据库，Web 界面刷新即可看到最新数据

## 📝 开发计划

- [ ] 支持更多大模型 API（Claude、本地模型等）
- [ ] 实现实际的豆瓣回复发送功能
- [ ] 添加 Web 界面
- [ ] 支持更多数据导出格式
- [ ] 添加定时任务功能

## 📄 许可证

本项目仅供学习和研究使用，请遵守相关法律法规和平台使用条款。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📮 联系方式

如有问题或建议，请通过 Issue 反馈。
