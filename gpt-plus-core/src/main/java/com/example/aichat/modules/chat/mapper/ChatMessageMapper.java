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

    Integer selectMaxSeqNo(@Param("sessionId") Long sessionId);
}
