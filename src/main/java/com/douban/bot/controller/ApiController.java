package com.douban.bot.controller;

import com.douban.bot.db.RepositoryService;
import com.douban.bot.model.Comment;
import com.douban.bot.model.Group;
import com.douban.bot.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final RepositoryService repository;

    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> getGroups() {
        try {
            List<Group> groups = repository.getAllGroups();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", groups);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取小组失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getPosts(
            @RequestParam(required = false) String group_id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        try {
            if (page < 1) page = 1;
            if (page_size < 1 || page_size > 100) page_size = 20;

            List<Post> posts = repository.getPostsWithPagination(group_id, page, page_size);
            int total = repository.getPostsCount(group_id);
            int pages = (total + page_size - 1) / page_size;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", posts);
            Map<String, Object> pagination = new HashMap<>();
            pagination.put("page", page);
            pagination.put("page_size", page_size);
            pagination.put("total", total);
            pagination.put("pages", pages);
            response.put("pagination", pagination);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取帖子失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<Map<String, Object>> getPost(@PathVariable String postId) {
        try {
            Post post = repository.getPostByPostID(postId);
            if (post == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Post not found");
                return ResponseEntity.status(404).body(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", post);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取帖子失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/comments/{postId}")
    public ResponseEntity<Map<String, Object>> getComments(@PathVariable String postId) {
        try {
            List<Comment> comments = repository.getCommentsByPostID(postId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", comments);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取评论失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = repository.getStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "获取统计信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
