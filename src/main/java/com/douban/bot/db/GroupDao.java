package com.douban.bot.db;

import com.douban.bot.model.Group;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public interface GroupDao {
    
    DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @SqlQuery("SELECT id as groupId, name, alt, member_count as memberCount, created, created_at as createdAt FROM \"Group\" WHERE id = :id")
    @RegisterBeanMapper(Group.class)
    Optional<Group> findById(@Bind("id") String id);

    @SqlUpdate("INSERT INTO \"Group\" (id, name, alt, member_count, created, created_at) VALUES (:groupId, :name, :alt, :memberCount, :created, :createdAt)")
    @Transaction
    void insert(@BindBean Group group, @Bind("created") String created, @Bind("createdAt") String createdAt);

    @SqlQuery("SELECT id as groupId, name, alt, member_count as memberCount, created, created_at as createdAt FROM \"Group\" ORDER BY created_at DESC")
    @RegisterBeanMapper(Group.class)
    List<Group> findAll();

    default void createGroup(Group group) {
        String created = group.getCreated() != null ? group.getCreated().format(DATE_FORMAT) : LocalDateTime.now().format(DATE_FORMAT);
        String createdAt = group.getCreatedAt() != null ? group.getCreatedAt().format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);
        insert(group, created, createdAt);
    }

    default Group getGroupById(String id) {
        return findById(id).orElse(null);
    }

    default List<Group> getAllGroups() {
        return findAll();
    }
}
