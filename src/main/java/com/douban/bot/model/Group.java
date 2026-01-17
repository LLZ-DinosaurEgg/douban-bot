package com.douban.bot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    private Long id;
    private String groupId;
    private String name;
    private String alt;
    private Integer memberCount;
    private LocalDateTime created;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
