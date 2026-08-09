package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 存储会话列表
 * @TableName ai_conversation_session
 */
@TableName(value ="ai_conversation_session")
@Data
public class AiConversationSession implements Serializable {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Integer user_id;

    /**
     * 会话记忆ID
     */
    @TableField(value = "memory_id")
    private Integer memory_id;

    /**
     * 会话主题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 对话时间
     */
    @TableField(value = "ceeate_time")
    private Date ceeate_time;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

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
        AiConversationSession other = (AiConversationSession) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUser_id() == null ? other.getUser_id() == null : this.getUser_id().equals(other.getUser_id()))
            && (this.getMemory_id() == null ? other.getMemory_id() == null : this.getMemory_id().equals(other.getMemory_id()))
            && (this.getTitle() == null ? other.getTitle() == null : this.getTitle().equals(other.getTitle()))
            && (this.getCeeate_time() == null ? other.getCeeate_time() == null : this.getCeeate_time().equals(other.getCeeate_time()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUser_id() == null) ? 0 : getUser_id().hashCode());
        result = prime * result + ((getMemory_id() == null) ? 0 : getMemory_id().hashCode());
        result = prime * result + ((getTitle() == null) ? 0 : getTitle().hashCode());
        result = prime * result + ((getCeeate_time() == null) ? 0 : getCeeate_time().hashCode());
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
        sb.append(", title=").append(title);
        sb.append(", ceeate_time=").append(ceeate_time);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}