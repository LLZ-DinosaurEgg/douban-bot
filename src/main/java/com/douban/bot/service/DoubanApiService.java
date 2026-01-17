package com.douban.bot.service;

import com.douban.bot.config.AppConfig;
import com.douban.bot.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoubanApiService {
    
    private final AppConfig appConfig;
    
    /**
     * 发送评论到豆瓣帖子
     * @param topicId 帖子ID
     * @param content 评论内容
     * @param cookie Cookie（用于身份验证）
     * @return 是否发送成功
     */
    public boolean postComment(String topicId, String content, String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) {
            log.error("Cookie为空，无法发送评论: topicId={}", topicId);
            return false;
        }
        
        if (content == null || content.trim().isEmpty()) {
            log.error("评论内容为空: topicId={}", topicId);
            return false;
        }
        
        try {
            // 先访问帖子页面，获取必要的token和session信息（模拟真实浏览器的行为）
            String postUrl = appConfig.getDoubanBaseHost() + "/group/topic/" + topicId + "/";
            String postPageContent = null;
            try {
                postPageContent = HttpUtils.fetchContent(postUrl, cookie);
                log.debug("已访问帖子页面: topicId={}", topicId);
            } catch (Exception e) {
                log.warn("访问帖子页面失败，继续尝试发送评论: topicId={}, error={}", topicId, e.getMessage());
            }
            
            // 从Cookie中提取ck（CSRF token）
            String ck = HttpUtils.extractCkFromCookie(cookie);
            
            // 如果Cookie中没有ck，尝试从页面中提取
            if ((ck == null || ck.isEmpty()) && postPageContent != null) {
                // 尝试从页面HTML中提取ck值
                Pattern ckPattern = Pattern.compile("ck['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
                Matcher matcher = ckPattern.matcher(postPageContent);
                if (matcher.find()) {
                    ck = matcher.group(1);
                    log.debug("从页面中提取到ck: topicId={}", topicId);
                }
            }
            
            if (ck == null || ck.isEmpty()) {
                log.warn("无法从Cookie或页面中提取ck（CSRF token），尝试继续发送: topicId={}", topicId);
            }
            
            // 构建评论URL
            String commentUrl = appConfig.getDoubanBaseHost() + "/group/topic/" + topicId + "/add_comment";
            
            // 构建表单数据（按照豆瓣的实际格式）
            StringBuilder formData = new StringBuilder();
            formData.append("rv_comment=").append(URLEncoder.encode(content, StandardCharsets.UTF_8));
            if (ck != null && !ck.isEmpty()) {
                formData.append("&ck=").append(URLEncoder.encode(ck, StandardCharsets.UTF_8));
            }
            formData.append("&start=0");
            formData.append("&submit_btn=").append(URLEncoder.encode("发送", StandardCharsets.UTF_8));
            
            log.info("准备发送评论到豆瓣: topicId={}, contentLength={}, url={}, hasCk={}", 
                    topicId, content.length(), commentUrl, ck != null && !ck.isEmpty());
            
            // 添加短暂延迟，模拟人类操作
            Thread.sleep(500 + (int)(Math.random() * 500));
            
            // 发送POST请求
            HttpUtils.PostResponse postResponse = HttpUtils.postFormDataWithStatus(commentUrl, cookie, postUrl, formData.toString());
            String response = postResponse.body;
            int statusCode = postResponse.statusCode;
            
            // 记录响应内容的前500个字符，用于调试
            String responsePreview = response != null && response.length() > 500 
                    ? response.substring(0, 500) + "..." 
                    : response;
            log.info("豆瓣评论接口响应: topicId={}, statusCode={}, responseLength={}, preview={}", 
                    topicId, statusCode, response != null ? response.length() : 0, responsePreview);
            
            // 检查响应是否成功
            if (response == null || response.trim().isEmpty()) {
                log.warn("评论发送响应为空: topicId={}", topicId);
                return false;
            }
            
            String lowerResponse = response.toLowerCase();
            
            // 检查明显的错误信息
            if (lowerResponse.contains("验证码") || lowerResponse.contains("captcha") 
                    || lowerResponse.contains("请输入验证码") || lowerResponse.contains("验证码错误")) {
                log.error("发送评论失败，需要验证码: topicId={}", topicId);
                return false;
            }
            if (lowerResponse.contains("登录") || lowerResponse.contains("login") 
                    || lowerResponse.contains("请先登录") || lowerResponse.contains("未登录")) {
                log.error("发送评论失败，需要登录或Cookie已失效: topicId={}", topicId);
                return false;
            }
            if (lowerResponse.contains("403") || lowerResponse.contains("forbidden")) {
                log.error("发送评论失败，403禁止访问: topicId={}", topicId);
                return false;
            }
            if (lowerResponse.contains("评论失败") || lowerResponse.contains("发送失败") 
                    || lowerResponse.contains("操作失败")) {
                log.error("发送评论失败，响应中包含失败信息: topicId={}", topicId);
                return false;
            }
            
            // 检查HTTP状态码：302重定向通常是成功的标志
            if (statusCode == 302) {
                log.info("评论发送成功（HTTP 302重定向）: topicId={}", topicId);
                return true;
            }
            
            // 检查成功标识：响应中包含评论内容的前20个字符（最可靠的判断）
            String contentPrefix = content.substring(0, Math.min(20, content.length()));
            if (response.contains(contentPrefix)) {
                log.info("评论发送成功（响应中包含评论内容）: topicId={}, contentLength={}", topicId, content.length());
                return true;
            }
            
            // 检查其他可能的成功标识
            // 豆瓣成功发送评论后，通常会重定向或返回包含特定标识的页面
            if (response.contains("评论已发布") || response.contains("评论成功") 
                    || response.contains("您的评论") || response.contains("已添加评论")) {
                log.info("评论发送成功（响应中包含成功标识）: topicId={}", topicId);
                return true;
            }
            
            // 如果状态码是200但响应是HTML页面且包含帖子内容，可能是成功但返回了帖子页面
            // 这种情况下需要更严格的检查
            if (statusCode == 200 && response.contains("group/topic/" + topicId) && response.length() > 1000) {
                // 响应包含帖子链接且内容较长，可能是成功但返回了帖子页面
                // 但为了安全，我们仍然认为可能失败，需要进一步验证
                log.warn("评论发送可能成功（HTTP 200且响应包含帖子链接），但无法确认: topicId={}, responseLength={}", 
                        topicId, response.length());
                // 为了安全，返回false，让用户知道需要验证
                return false;
            }
            
            // 如果以上都不匹配，认为发送失败
            log.warn("评论发送失败（无法确认成功）: topicId={}, statusCode={}, responseLength={}, responsePreview={}", 
                    topicId, statusCode, response.length(), responsePreview);
            return false;
            
        } catch (IOException e) {
            log.error("发送评论到豆瓣失败: topicId={}, error={}", topicId, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("发送评论时发生未知错误: topicId={}, error={}", topicId, e.getMessage(), e);
            return false;
        }
    }
}
