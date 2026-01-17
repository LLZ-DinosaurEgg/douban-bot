package com.douban.bot.db;

import com.douban.bot.model.Post;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PostDao {
    
    DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    ObjectMapper objectMapper = new ObjectMapper();
    
    @SqlQuery("SELECT id, post_id as postId, group_id as groupId, author_info as authorInfo, alt, title, content, " +
            "photo_list as photoList, is_matched as isMatched, keyword_list as keywordList, " +
            "created, updated, created_at as createdAt FROM \"Post\" WHERE post_id = :postId")
    @RegisterConstructorMapper(PostRow.class)
    Optional<PostRow> findByPostId(@Bind("postId") String postId);

    @SqlQuery("SELECT id, post_id as postId, group_id as groupId, author_info as authorInfo, alt, title, content, " +
            "photo_list as photoList, is_matched as isMatched, keyword_list as keywordList, " +
            "created, updated, created_at as createdAt FROM \"Post\" WHERE title = :title LIMIT 1")
    @RegisterConstructorMapper(PostRow.class)
    Optional<PostRow> findByTitle(@Bind("title") String title);

    @SqlQuery("SELECT COUNT(*) FROM \"Post\" WHERE (:groupId IS NULL OR group_id = :groupId)")
    int countPosts(@Bind("groupId") String groupId);

    @SqlQuery("SELECT id, post_id as postId, group_id as groupId, author_info as authorInfo, alt, title, content, " +
            "photo_list as photoList, is_matched as isMatched, keyword_list as keywordList, " +
            "created, updated, created_at as createdAt FROM \"Post\" " +
            "WHERE (:groupId IS NULL OR group_id = :groupId) " +
            "ORDER BY created DESC LIMIT :limit OFFSET :offset")
    @RegisterConstructorMapper(PostRow.class)
    List<PostRow> findWithPagination(@Bind("groupId") String groupId, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT id, post_id as postId, group_id as groupId, author_info as authorInfo, alt, title, content, " +
            "photo_list as photoList, is_matched as isMatched, keyword_list as keywordList, " +
            "created, updated, created_at as createdAt FROM \"Post\" " +
            "WHERE group_id = :groupId ORDER BY created DESC LIMIT :limit")
    @RegisterConstructorMapper(PostRow.class)
    List<PostRow> findByGroupId(@Bind("groupId") String groupId, @Bind("limit") int limit);

    @SqlUpdate("INSERT INTO \"Post\" (post_id, group_id, author_info, alt, title, content, photo_list, " +
            "is_matched, keyword_list, created, updated) " +
            "VALUES (:postId, :groupId, :authorInfo, :alt, :title, :content, :photoList, " +
            ":isMatched, :keywordList, :created, :updated)")
    @Transaction
    void insert(@Bind("postId") String postId,
                @Bind("groupId") String groupId,
                @Bind("authorInfo") String authorInfo,
                @Bind("alt") String alt,
                @Bind("title") String title,
                @Bind("content") String content,
                @Bind("photoList") String photoList,
                @Bind("isMatched") boolean isMatched,
                @Bind("keywordList") String keywordList,
                @Bind("created") String created,
                @Bind("updated") String updated);

    @SqlUpdate("UPDATE \"Post\" SET title = :title, updated = :updated WHERE post_id = :postId")
    @Transaction
    void update(@Bind("postId") String postId, @Bind("title") String title, @Bind("updated") String updated);

    default Post getPostByPostId(String postId) {
        return findByPostId(postId).map(this::toPost).orElse(null);
    }

    default boolean checkPostTitleExists(String title) {
        return findByTitle(title).isPresent();
    }

    default void createPost(Post post) {
        PostRow row = toPostRow(post);
        insert(row.postId(), row.groupId(), row.authorInfo(), row.alt(), row.title(),
                row.content(), row.photoList(), row.isMatched(), row.keywordList(),
                row.created(), row.updated());
    }

    default void updatePost(Post post) {
        String updated = post.getUpdated() != null ? post.getUpdated().format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);
        update(post.getPostId(), post.getTitle(), updated);
    }

    default List<Post> getPostsWithPagination(String groupId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<PostRow> rows = findWithPagination(groupId, pageSize, offset);
        return rows.stream().map(this::toPost).toList();
    }

    default int getPostsCount(String groupId) {
        return countPosts(groupId);
    }

    default List<Post> getPostsByGroupId(String groupId, int limit) {
        List<PostRow> rows = findByGroupId(groupId, limit);
        return rows.stream().map(this::toPost).toList();
    }

    private PostRow toPostRow(Post post) {
        try {
            String authorInfoJson = post.getAuthorInfo() != null ? objectMapper.writeValueAsString(post.getAuthorInfo()) : "{}";
            String photoListJson = post.getPhotoList() != null ? objectMapper.writeValueAsString(post.getPhotoList()) : "[]";
            String keywordListJson = post.getKeywordList() != null ? objectMapper.writeValueAsString(post.getKeywordList()) : "[]";
            String created = post.getCreated() != null ? post.getCreated().format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);
            String updated = post.getUpdated() != null ? post.getUpdated().format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);

            return new PostRow(
                    post.getPostId(), post.getGroupId(), authorInfoJson, post.getAlt(), post.getTitle(),
                    post.getContent(), photoListJson, post.getIsMatched() != null && post.getIsMatched(),
                    keywordListJson, created, updated
            );
        } catch (Exception e) {
            throw new RuntimeException("Error converting Post to PostRow", e);
        }
    }

    private Post toPost(PostRow row) {
        try {
            Map<String, Object> authorInfo = row.authorInfo() != null && !row.authorInfo().isEmpty() && !row.authorInfo().equals("{}")
                    ? objectMapper.readValue(row.authorInfo(), new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            List<String> photoList = row.photoList() != null && !row.photoList().isEmpty() && !row.photoList().equals("[]")
                    ? objectMapper.readValue(row.photoList(), new TypeReference<List<String>>() {})
                    : List.of();
            List<String> keywordList = row.keywordList() != null && !row.keywordList().isEmpty() && !row.keywordList().equals("[]")
                    ? objectMapper.readValue(row.keywordList(), new TypeReference<List<String>>() {})
                    : List.of();

            LocalDateTime created = row.created() != null ? LocalDateTime.parse(row.created(), DATETIME_FORMAT) : LocalDateTime.now();
            LocalDateTime updated = row.updated() != null ? LocalDateTime.parse(row.updated(), DATETIME_FORMAT) : LocalDateTime.now();

            return Post.builder()
                    .postId(row.postId())
                    .groupId(row.groupId())
                    .authorInfo(authorInfo)
                    .alt(row.alt())
                    .title(row.title())
                    .content(row.content())
                    .photoList(photoList)
                    .isMatched(row.isMatched())
                    .keywordList(keywordList)
                    .created(created)
                    .updated(updated)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error converting PostRow to Post", e);
        }
    }

    record PostRow(
            String postId, String groupId, String authorInfo, String alt, String title,
            String content, String photoList, boolean isMatched, String keywordList,
            String created, String updated
    ) {}
}
