package com.douban.bot.db;

import com.douban.bot.model.Comment;
import com.douban.bot.model.CrawlerConfig;
import com.douban.bot.model.Group;
import com.douban.bot.model.Post;
import org.jdbi.v3.core.Jdbi;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryService {
    
    private final GroupDao groupDao;
    private final PostDao postDao;
    private final CommentDao commentDao;
    private final CrawlerConfigDao crawlerConfigDao;
    private final Jdbi jdbi;

    public RepositoryService(Jdbi jdbi) {
        this.jdbi = jdbi;
        this.groupDao = jdbi.onDemand(GroupDao.class);
        this.postDao = jdbi.onDemand(PostDao.class);
        this.commentDao = jdbi.onDemand(CommentDao.class);
        this.crawlerConfigDao = jdbi.onDemand(CrawlerConfigDao.class);
    }

    // Group methods
    public Group getGroupById(String id) {
        return groupDao.getGroupById(id);
    }

    public void createGroup(Group group) {
        groupDao.createGroup(group);
    }

    public List<Group> getAllGroups() {
        return groupDao.getAllGroups();
    }

    // Post methods
    public Post getPostByPostID(String postId) {
        return postDao.getPostByPostId(postId);
    }

    public void createPost(Post post) {
        postDao.createPost(post);
    }

    public void updatePost(Post post) {
        postDao.updatePost(post);
    }
    
    public void updatePostBotReply(Post post) {
        postDao.updateBotReply(post);
    }

    public boolean checkPostTitleExists(String title) {
        return postDao.checkPostTitleExists(title);
    }

    public List<Post> getPostsWithPagination(String groupId, int page, int pageSize, String botReplied, String sortOrder) {
        return postDao.getPostsWithPagination(groupId, page, pageSize, botReplied, sortOrder);
    }

    public int getPostsCount(String groupId, String botReplied) {
        return postDao.getPostsCount(groupId, botReplied);
    }

    public List<Post> getPostsByGroupId(String groupId, int limit) {
        return postDao.getPostsByGroupId(groupId, limit);
    }
    
    public Post getOneUnrepliedPost() {
        return postDao.getOneUnrepliedPost();
    }

    // Comment methods
    public Comment getCommentByCommentID(String commentId) {
        return commentDao.getCommentByCommentId(commentId);
    }

    public void createComment(Comment comment) {
        commentDao.createComment(comment);
    }

    public List<Comment> getCommentsByPostID(String postId) {
        return commentDao.getCommentsByPostId(postId);
    }

    public List<Comment> getCommentsByGroupId(String groupId, int limit) {
        return commentDao.getCommentsByGroupId(groupId, limit);
    }

    // Stats
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("groups", getAllGroups().size());
        stats.put("posts", postDao.getPostsCount(null, null)); // 查询所有帖子，不筛选
        // TODO: Add comment count query
        stats.put("comments", 0);
        return stats;
    }

    // CrawlerConfig methods
    public List<CrawlerConfig> getAllCrawlerConfigs() {
        return crawlerConfigDao.getAllConfigs();
    }

    public CrawlerConfig getCrawlerConfigById(Long id) {
        return crawlerConfigDao.getConfigById(id).orElse(null);
    }
    
    public List<CrawlerConfig> getEnabledCrawlerConfigs() {
        return crawlerConfigDao.getAllConfigs().stream()
                .filter(c -> c.getEnabled() != null && c.getEnabled())
                .toList();
    }
}
