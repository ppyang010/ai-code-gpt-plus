package com.example.aichat.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.chat.entity.ChatMessageDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {

    List<ChatMessageDO> selectBySessionIdOrderBySeqNo(@Param("sessionId") Long sessionId);

    List<ChatMessageDO> selectLatestMessages(
            @Param("sessionId") Long sessionId,
            @Param("limit") Integer limit
    );

    /**
     * 查询会话内首条用户消息，用于默认会话标题的历史兜底展示。
     */
    ChatMessageDO selectFirstUserMessage(@Param("sessionId") Long sessionId);

    List<ChatMessageDO> selectLatestMessagesBeforeSeqNo(
            @Param("sessionId") Long sessionId,
            @Param("beforeSeqNo") Integer beforeSeqNo,
            @Param("limit") Integer limit
    );

    ChatMessageDO selectPreviousUserMessage(
            @Param("sessionId") Long sessionId,
            @Param("seqNo") Integer seqNo
    );

    Integer selectMaxSeqNo(@Param("sessionId") Long sessionId);

    int updateStatusByIdAndCurrentStatus(
            @Param("messageId") Long messageId,
            @Param("expectedStatus") Integer expectedStatus,
            @Param("targetStatus") Integer targetStatus
    );
}
