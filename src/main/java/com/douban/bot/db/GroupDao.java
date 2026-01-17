package com.douban.bot.db;

import com.douban.bot.model.Group;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public interface GroupDao {
    
    DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @SqlQuery("SELECT id as groupId, name, alt, member_count as memberCount, created, created_at as createdAt FROM \"Group\" WHERE id = :id")
    @RegisterConstructorMapper(GroupRow.class)
    Optional<GroupRow> findById(@Bind("id") String id);

    @SqlUpdate("INSERT INTO \"Group\" (id, name, alt, member_count, created, created_at) VALUES (:groupId, :name, :alt, :memberCount, :created, :createdAt)")
    @Transaction
    void insert(@BindBean Group group, @Bind("created") String created, @Bind("createdAt") String createdAt);

    @SqlQuery("SELECT id as groupId, name, alt, member_count as memberCount, created, created_at as createdAt FROM \"Group\" ORDER BY created_at DESC")
    @RegisterConstructorMapper(GroupRow.class)
    List<GroupRow> findAll();

    default void createGroup(Group group) {
        String created = group.getCreated() != null ? group.getCreated().format(DATE_FORMAT) : LocalDate.now().format(DATE_FORMAT);
        String createdAt = group.getCreatedAt() != null ? group.getCreatedAt().format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);
        insert(group, created, createdAt);
    }

    default Group getGroupById(String id) {
        return findById(id).map(this::toGroup).orElse(null);
    }

    default List<Group> getAllGroups() {
        return findAll().stream().map(this::toGroup).toList();
    }

    private Group toGroup(GroupRow row) {
        LocalDate created = row.created() != null && !row.created().isEmpty()
                ? LocalDate.parse(row.created(), DATE_FORMAT)
                : LocalDate.now();
        LocalDateTime createdAt = row.createdAt() != null && !row.createdAt().isEmpty()
                ? LocalDateTime.parse(row.createdAt(), DATETIME_FORMAT)
                : LocalDateTime.now();

        return Group.builder()
                .id(null) // Group 使用 groupId 作为主键
                .groupId(row.groupId())
                .name(row.name())
                .alt(row.alt())
                .memberCount(row.memberCount())
                .created(created)
                .createdAt(createdAt)
                .build();
    }

    record GroupRow(
            String groupId, String name, String alt, Integer memberCount,
            String created, String createdAt
    ) {}
}
