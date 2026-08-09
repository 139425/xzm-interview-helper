package com.xzm.xzm_interview_helper.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzm.xzm_interview_helper.model.dto.ChatHistorySummaryDTO;
import com.xzm.xzm_interview_helper.model.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author 34631
* @description 针对表【ai_conversation(存储 AI 对话记录：用户提问与 AI 回复)】的数据库操作Mapper
* @createDate 2025-08-02 19:35:10
* @Entity generator.domain.AiConversation
*/
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    @Select("""
            SELECT COUNT(DISTINCT memory_id)
            FROM ai_conversation
            WHERE user_id = #{userId}
            """)
    long countDistinctMemoryIdByUser(@Param("userId") int userId);

    @Select("""
            SELECT t.memory_id AS memoryId,
                   t.chat_time AS lastChatTime,
                   t.question AS lastQuestion,
                   agg.message_count AS messageCount
            FROM ai_conversation t
            JOIN (
                SELECT memory_id,
                       MAX(chat_time) AS max_time,
                       COUNT(*) AS message_count
                FROM ai_conversation
                WHERE user_id = #{userId}
                GROUP BY memory_id
            ) agg
                ON t.memory_id = agg.memory_id
               AND t.chat_time = agg.max_time
            WHERE t.user_id = #{userId}
            ORDER BY t.chat_time DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<ChatHistorySummaryDTO> selectHistorySummariesByUserPaged(
            @Param("userId") int userId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize
    );
}
