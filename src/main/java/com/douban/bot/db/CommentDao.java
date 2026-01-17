package com.douban.bot.db;

import com.douban.bot.model.Comment;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CommentDao {
    
    DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ObjectMapper objectMapper = new ObjectMapper();

    @SqlQuery("SELECT id, comment_id as commentId, post_id as postId, group_id as groupId, " +
            "author_info as authorInfo, content, reply_to_id as replyToId, like_count as likeCount, " +
            "created, created_at as createdAt FROM \"Comment\" WHERE comment_id = :commentId")
    Optional<CommentRow> findByCommentId(@Bind("commentId") String commentId);

    @SqlQuery("SELECT id, comment_id as commentId, post_id as postId, group_id as groupId, " +
            "author_info as authorInfo, content, reply_to_id as replyToId, like_count as likeCount, " +
            "created, created_at as createdAt FROM \"Comment\" WHERE post_id = :postId ORDER BY created ASC")
    @RegisterBeanMapper(CommentRow.class)
    List<CommentRow> findByPostId(@Bind("postId") String postId);

    @SqlQuery("SELECT id, comment_id as commentId, post_id as postId, group_id as groupId, " +
            "author_info as authorInfo, content, reply_to_id as replyToId, like_count as likeCount, " +
            "created, created_at as createdAt FROM \"Comment\" " +
            "WHERE group_id = :groupId ORDER BY created DESC LIMIT :limit")
    @RegisterBeanMapper(CommentRow.class)
    List<CommentRow> findByGroupId(@Bind("groupId") String groupId, @Bind("limit") int limit);

    @SqlUpdate("INSERT INTO \"Comment\" (comment_id, post_id, group_id, author_info, content, reply_to_id, like_count, created) " +
            "VALUES (:commentId, :postId, :groupId, :authorInfo, :content, :replyToId, :likeCount, :created)")
    @GetGeneratedKeys("id")
    @Transaction
    long insert(CommentRow row);

    default Comment getCommentByCommentId(String commentId) {
        return findByCommentId(commentId).map(this::toComment).orElse(null);
    }

    default List<Comment> getCommentsByPostId(String postId) {
        List<CommentRow> rows = findByPostId(postId);
        return rows.stream().map(this::toComment).toList();
    }

    default List<Comment> getCommentsByGroupId(String groupId, int limit) {
        List<CommentRow> rows = findByGroupId(groupId, limit);
        return rows.stream().map(this::toComment).toList();
    }

    default void createComment(Comment comment) {
        CommentRow row = toCommentRow(comment);
        insert(row);
    }

    private CommentRow toCommentRow(Comment comment) {
        try {
            String authorInfoJson = comment.getAuthorInfo() != null ? objectMapper.writeValueAsString(comment.getAuthorInfo()) : "{}";
            String created = comment.getCreated() != null ? comment.getCreated().format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);
            
            return new CommentRow(
                    comment.getCommentId(), comment.getPostId(), comment.getGroupId(),
                    authorInfoJson, comment.getContent(), comment.getReplyToId(),
                    comment.getLikeCount() != null ? comment.getLikeCount() : 0, created
            );
        } catch (Exception e) {
            throw new RuntimeException("Error converting Comment to CommentRow", e);
        }
    }

    private Comment toComment(CommentRow row) {
        try {
            Map<String, Object> authorInfo = row.authorInfo() != null && !row.authorInfo().isEmpty() && !row.authorInfo().equals("{}")
                    ? objectMapper.readValue(row.authorInfo(), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            
            LocalDateTime created = row.created() != null ? LocalDateTime.parse(row.created(), DATETIME_FORMAT) : LocalDateTime.now();

            return Comment.builder()
                    .commentId(row.commentId())
                    .postId(row.postId())
                    .groupId(row.groupId())
                    .authorInfo(authorInfo)
                    .content(row.content())
                    .replyToId(row.replyToId())
                    .likeCount(row.likeCount())
                    .created(created)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error converting CommentRow to Comment", e);
        }
    }

    record CommentRow(
            String commentId, String postId, String groupId, String authorInfo,
            String content, String replyToId, int likeCount, String created
    ) {}
}
