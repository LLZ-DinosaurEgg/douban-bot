package utils

import (
	"math/rand"
	"net/http"
	"time"

	"github.com/LLZ-DinosaurEgg/douban-crawler/config"
)

var seeded = false

// utilsRandSeed 初始化随机种子（只执行一次）
func UtilsRandSeed() {
	if !seeded {
		rand.Seed(time.Now().UnixNano())
		seeded = true
	}
}

// NewHTTPClient 创建HTTP客户端
func NewHTTPClient() *http.Client {
	return &http.Client{
		Timeout: 30 * time.Second,
	}
}

// GetRandomUserAgent 获取随机UserAgent（这里简化为固定值）
func GetRandomUserAgent() string {
	return config.USER_AGENT
}

// GetRandomSleep 获取随机睡眠时长（毫秒）
func GetRandomSleep(minMs, maxMs int) time.Duration {
	if maxMs <= minMs {
		return time.Duration(minMs) * time.Millisecond
	}
	return time.Duration(rand.Intn(maxMs-minMs)+minMs) * time.Millisecond
}
