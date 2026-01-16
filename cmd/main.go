package main

import (
	"flag"
	"log"
	"strings"
	"time"

	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/bot"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/crawler"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/sqlite"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/utils"
)

func main() {
	// 初始化随机种子（只做一次）
	// 这样 utils.GetRandomSleep 中就不需要每次都 seed
	// 放在 main 中可保证程序生命周期内只 seed 一次
	utilsSeed()

	// 解析命令行参数
	var (
		groups         = flag.String("g", "", "小组ID，多个用逗号分隔")
		keywords       = flag.String("k", "", "关键词，多个用逗号分隔")
		exclude        = flag.String("e", "", "排除关键词，多个用逗号分隔")
		sleep          = flag.Int("sleep", 900, "睡眠时长（秒）")
		pages          = flag.Int("pages", 10, "爬取页数")
		debug          = flag.Bool("v", false, "调试模式")
		enableBot      = flag.Bool("bot", false, "启用自动回复机器人")
		replyKeywords  = flag.String("reply-keywords", "", "需要回复的关键词，多个用逗号分隔（为空则回复所有匹配的帖子）")
		minReplyDelay  = flag.Int("min-reply-delay", 30, "最小回复延迟（秒）")
		maxReplyDelay  = flag.Int("max-reply-delay", 300, "最大回复延迟（秒）")
		maxHistoryPosts = flag.Int("max-history-posts", 50, "用于学习风格的最大历史帖子数")
		maxHistoryComments = flag.Int("max-history-comments", 200, "用于学习风格的最大历史评论数")
	)
	flag.Parse()

	if *debug {
		log.Println("debug 模式已启用")
	}

	// 解析参数
	groupList := []string{}
	keywordList := []string{}
	excludeList := []string{}
	if *groups != "" {
		groupList = strings.Split(*groups, ",")
	}
	if *keywords != "" {
		keywordList = strings.Split(*keywords, ",")
	}
	if *exclude != "" {
		excludeList = strings.Split(*exclude, ",")
	}

	// 初始化数据库
	db, err := sqlite.NewSQLiteDB("./db.sqlite3")
	if err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}
	defer db.Close()

	// 创建爬虫
	cr := crawler.NewCrawler(db)

	// 创建机器人（如果启用）
	var replyBot *bot.ReplyBot
	if *enableBot {
		replyKeywordList := []string{}
		if *replyKeywords != "" {
			replyKeywordList = strings.Split(*replyKeywords, ",")
		}
		botConfig := &config.BotConfig{
			EnableAutoReply:   true,
			ReplyKeywords:    replyKeywordList,
			MinReplyDelay:    *minReplyDelay,
			MaxReplyDelay:    *maxReplyDelay,
			MaxHistoryPosts:  *maxHistoryPosts,
			MaxHistoryComments: *maxHistoryComments,
		}
		replyBot = bot.NewReplyBot(db, botConfig)
		log.Println("自动回复机器人已启用")
	}

	// 循环爬取（简单实现）
	for {
		for _, groupID := range groupList {
			if groupID == "" {
				continue
			}
			
			// 爬取帖子
			if err := cr.Crawl(groupID, *pages, keywordList, excludeList); err != nil {
				log.Printf("爬取小组 %s 失败: %v", groupID, err)
			}

			// 如果启用了机器人，处理自动回复
			if *enableBot && replyBot != nil {
				if err := replyBot.ProcessNewPosts(groupID); err != nil {
					log.Printf("处理自动回复失败: %v", err)
				}
			}
		}

		log.Printf("睡眠 %d 秒...", *sleep)
		time.Sleep(time.Duration(*sleep) * time.Second)
	}
}

func utilsSeed() {
	// 将 seed 放在程序启动时执行一次
	// 如果你希望可配置化，可以把这个函数移到 config 或 main 中并通过 flag 控制
	// 这里直接调用 rand.Seed
	utils.UtilsRandSeed()
}
