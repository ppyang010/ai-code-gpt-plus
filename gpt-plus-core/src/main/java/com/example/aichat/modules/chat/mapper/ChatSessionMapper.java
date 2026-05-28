package com.example.aichat.modules.chat.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.chat.entity.ChatSessionDO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionDO> {

    IPage<ChatSessionDO> selectPageByUserId(
            IPage<ChatSessionDO> page,
            @Param("userId") Long userId
    );

    ChatSessionDO selectActiveById(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );

    int updateLastMessageAt(
            @Param("sessionId") Long sessionId,
            @Param("lastMessageAt") LocalDateTime lastMessageAt
    );
}
