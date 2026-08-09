package com.xzm.xzm_interview_helper.model.entity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 存储 AI 对话记录：用户提问与 AI 回复
 * @TableName ai_conversation
 */
@TableName(value ="ai_conversation")
@Data
public class AiConversation {
    /**
     * 
     */
    @TableId
    private Long id;

    /**
     * 
     */
    private Integer user_id;

    /**
     * 
     */
    private Integer memory_id;

    /**
     * 
     */
    private String question;

    /**
     * 
     */
    private String message;

    /**
     * AI回复内容
     */
    private String record;

    /**
     * 思考过程内容
     */
    private String thinking;

    /**
     * 
     */
    private Date chat_time;

    // Getter and Setter methods
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUser_id() {
        return user_id;
    }

    public void setUser_id(Integer user_id) {
        this.user_id = user_id;
    }

    public Integer getMemory_id() {
        return memory_id;
    }

    public void setMemory_id(Integer memory_id) {
        this.memory_id = memory_id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRecord() {
        return record;
    }

    public void setRecord(String record) {
        this.record = record;
    }

    public String getThinking() {
        return thinking;
    }

    public void setThinking(String thinking) {
        this.thinking = thinking;
    }

    public Date getChat_time() {
        return chat_time;
    }

    public void setChat_time(Date chat_time) {
        this.chat_time = chat_time;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        AiConversation other = (AiConversation) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUser_id() == null ? other.getUser_id() == null : this.getUser_id().equals(other.getUser_id()))
            && (this.getMemory_id() == null ? other.getMemory_id() == null : this.getMemory_id().equals(other.getMemory_id()))
            && (this.getQuestion() == null ? other.getQuestion() == null : this.getQuestion().equals(other.getQuestion()))
            && (this.getMessage() == null ? other.getMessage() == null : this.getMessage().equals(other.getMessage()))
            && (this.getRecord() == null ? other.getRecord() == null : this.getRecord().equals(other.getRecord()))
            && (this.getThinking() == null ? other.getThinking() == null : this.getThinking().equals(other.getThinking()))
            && (this.getChat_time() == null ? other.getChat_time() == null : this.getChat_time().equals(other.getChat_time()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUser_id() == null) ? 0 : getUser_id().hashCode());
        result = prime * result + ((getMemory_id() == null) ? 0 : getMemory_id().hashCode());
        result = prime * result + ((getQuestion() == null) ? 0 : getQuestion().hashCode());
        result = prime * result + ((getMessage() == null) ? 0 : getMessage().hashCode());
        result = prime * result + ((getRecord() == null) ? 0 : getRecord().hashCode());
        result = prime * result + ((getThinking() == null) ? 0 : getThinking().hashCode());
        result = prime * result + ((getChat_time() == null) ? 0 : getChat_time().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", user_id=").append(user_id);
        sb.append(", memory_id=").append(memory_id);
        sb.append(", question=").append(question);
        sb.append(", message=").append(message);
        sb.append(", record=").append(record);
        sb.append(", thinking=").append(thinking);
        sb.append(", chat_time=").append(chat_time);
        sb.append("]");
        return sb.toString();
    }
}