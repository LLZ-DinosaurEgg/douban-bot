package config

import "time"

// 全局配置
var (
	DOUBAN_BASE_HOST      = "https://www.douban.com"
	GROUP_TOPICS_BASE_URL = DOUBAN_BASE_HOST + "/group/%s/discussion"
	GROUP_INFO_BASE_URL   = DOUBAN_BASE_HOST + "/group/%s/"
	USER_AGENT            = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36"
	COOKIE                = "" // 替换为你的Cookie
	DATETIME_FORMAT       = "2006-01-02 15:04:05"
	DATE_FORMAT           = "2006-01-02"
	IMG_HEIGHT            = 400
	IMG_WIDTH             = 400
)

// LLM配置
var (
	LLM_API_TYPE    = "openai"              // 大模型类型: openai, claude, custom
	LLM_API_BASE    = "https://api.openai.com/v1" // API基础URL
	LLM_API_KEY     = ""                    // API密钥
	LLM_MODEL       = "gpt-3.5-turbo"       // 模型名称
	LLM_TEMPERATURE = 0.7                   // 温度参数
	LLM_MAX_TOKENS  = 500                   // 最大token数
)

// CrawlerConfig 爬虫配置
type CrawlerConfig struct {
	Groups   []string
	Keywords []string
	Exclude  []string
	Sleep    time.Duration
	Pages    int
	Debug    bool
}

// BotConfig 机器人配置
type BotConfig struct {
	EnableAutoReply bool     // 是否启用自动回复
	ReplyKeywords   []string // 需要回复的关键词（如果为空则回复所有匹配的帖子）
	MinReplyDelay   int      // 最小回复延迟（秒）
	MaxReplyDelay   int      // 最大回复延迟（秒）
	MaxHistoryPosts int      // 用于学习风格的最大历史帖子数
	MaxHistoryComments int   // 用于学习风格的最大历史评论数
}
