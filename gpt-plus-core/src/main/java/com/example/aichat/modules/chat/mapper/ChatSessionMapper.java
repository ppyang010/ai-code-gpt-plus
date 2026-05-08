package com.example.aichat.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.chat.entity.ChatSessionDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionDO> {

    List<ChatSessionDO> selectPageByUserId(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );

    Long countByUserId(@Param("userId") Long userId);

    ChatSessionDO selectActiveById(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );

    int updateLastMessageAt(
            @Param("sessionId") Long sessionId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt
    );
}
