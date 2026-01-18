package com.douban.bot.utils;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

public class HttpUtils {

    private static final Random random = new Random();
    
    public static CloseableHttpClient createHttpClient() {
        return HttpClients.createDefault();
    }

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"
    );

    public static String getUserAgent() {
        return USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
    }

    public static void randomSleep(int minMs, int maxMs) {
        if (maxMs <= minMs) {
            sleep(minMs);
            return;
        }
        int sleepTime = random.nextInt(maxMs - minMs) + minMs;
        sleep(sleepTime);
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class GetResponse {
        public final int statusCode;
        public final String body;

        public GetResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    public static GetResponse fetchContentWithStatus(String url, String cookie, String referer) throws IOException {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", getUserAgent());
            request.setHeader("Cookie", cookie);
            if (referer != null && !referer.isBlank()) {
                request.setHeader("Referer", referer);
            }
            request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
            request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            // Apache HttpClient 默认不支持 br 解压，避免返回乱码
            request.setHeader("Accept-Encoding", "gzip, deflate");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("Upgrade-Insecure-Requests", "1");
            request.setHeader("Sec-Fetch-Dest", "document");
            request.setHeader("Sec-Fetch-Mode", "navigate");
            request.setHeader("Sec-Fetch-Site", "same-origin");
            request.setHeader("Sec-Fetch-User", "?1");
            request.setHeader("Cache-Control", "max-age=0");

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return new GetResponse(statusCode, responseBody);
            } catch (ParseException e) {
                throw new IOException("Failed to parse response entity", e);
            }
        }
    }

    public static String fetchContent(String url, String cookie) throws IOException {
        GetResponse response = fetchContentWithStatus(url, cookie, null);
        if (response.statusCode != 200 && response.statusCode != 302) {
            String preview = response.body != null && response.body.length() > 200
                    ? response.body.substring(0, 200) + "..."
                    : response.body;
            throw new IOException("HTTP request failed with status: " + response.statusCode + ", response: " + preview);
        }
        return response.body;
    }
    
    /**
     * 从Cookie中提取ck（CSRF token）
     */
    public static String extractCkFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return null;
        }
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("ck=")) {
                return trimmed.substring(3);
            }
        }
        return null;
    }
    
    /**
     * 发送POST请求（表单数据），返回响应内容和状态码
     */
    public static class PostResponse {
        public final int statusCode;
        public final String body;
        
        public PostResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
    
    /**
     * 发送POST请求（表单数据）
     */
    public static String postFormData(String url, String cookie, String referer, String formData) throws IOException {
        PostResponse response = postFormDataWithStatus(url, cookie, referer, formData);
        return response.body;
    }
    
    /**
     * 发送POST请求（表单数据），返回响应对象（包含状态码）
     */
    public static PostResponse postFormDataWithStatus(String url, String cookie, String referer, String formData) throws IOException {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpPost request = new HttpPost(url);
            
            // 设置完整的浏览器请求头，模拟真实浏览器
            request.setHeader("User-Agent", getUserAgent());
            request.setHeader("Cookie", cookie);
            request.setHeader("Referer", referer);
            request.setHeader("Origin", "https://www.douban.com");
            request.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            request.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            // Apache HttpClient 默认不支持 br 解压，避免返回乱码
            request.setHeader("Accept-Encoding", "gzip, deflate");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("Upgrade-Insecure-Requests", "1");
            request.setHeader("Sec-Fetch-Dest", "document");
            request.setHeader("Sec-Fetch-Mode", "navigate");
            request.setHeader("Sec-Fetch-Site", "same-origin");
            request.setHeader("Sec-Fetch-User", "?1");
            request.setHeader("Cache-Control", "max-age=0");
            
            request.setEntity(new StringEntity(formData, ContentType.APPLICATION_FORM_URLENCODED));
            
            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                // 豆瓣可能返回200、302（重定向）或403
                // 注意：即使返回403，评论可能已经成功发送，需要在调用方验证
                // 不在这里抛出异常，而是返回403状态码，让调用方决定如何处理
                
                if (statusCode != 200 && statusCode != 302 && statusCode != 403) {
                    throw new IOException("POST request failed with status: " + statusCode + ", response: " + 
                            (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
                }
                
                return new PostResponse(statusCode, responseBody);
            } catch (ParseException e) {
                throw new IOException("Failed to parse response entity", e);
            }
        }
    }
}
