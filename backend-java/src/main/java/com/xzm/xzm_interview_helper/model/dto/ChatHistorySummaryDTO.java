package com.xzm.xzm_interview_helper.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

/**
 * 聊天历史摘要DTO
 * 用于侧边栏显示历史记录列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistorySummaryDTO {
    /**
     * 会话ID
     */
    private Integer memoryId;
    
    /**
     * 最近一次对话时间
     */
    private Date lastChatTime;
    
    /**
     * 最近一次用户问题
     */
    private String lastQuestion;
    
    /**
     * 对话总数
     */
    private Long messageCount;

    /** Opaque id used in /chat/{conversationId}; never exposes another user's data. */
    private String conversationId;

    // Getter and Setter methods
    public Integer getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Integer memoryId) {
        this.memoryId = memoryId;
    }

    public Date getLastChatTime() {
        return lastChatTime;
    }

    public void setLastChatTime(Date lastChatTime) {
        this.lastChatTime = lastChatTime;
    }

    public String getLastQuestion() {
        return lastQuestion;
    }

    public void setLastQuestion(String lastQuestion) {
        this.lastQuestion = lastQuestion;
    }

    public Long getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Long messageCount) {
        this.messageCount = messageCount;
    }
}
