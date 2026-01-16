package main

import (
	"flag"
	"log"

	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/db/sqlite"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/server"
	"github.com/LLZ-DinosaurEgg/douban-crawler/internal/utils"
)

func main() {
	// 初始化随机种子
	utils.UtilsRandSeed()

	// 解析命令行参数
	var (
		port = flag.String("port", "8080", "Web服务器端口")
		dbPath = flag.String("db", "./db.sqlite3", "数据库文件路径")
	)
	flag.Parse()

	// 初始化数据库
	db, err := sqlite.NewSQLiteDB(*dbPath)
	if err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}
	defer db.Close()

	// 创建并启动Web服务器
	srv := server.NewServer(db, *port)
	if err := srv.Start(); err != nil {
		log.Fatalf("启动Web服务器失败: %v", err)
	}
}
