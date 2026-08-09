package com.xzm.xzm_interview_helper.model.entity;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 存储 面试记录，每一次面试存在一条记录中
 * @TableName ai_interview
 */
@TableName(value ="ai_interview")
@Data
public class AiInterview implements Serializable {
    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 用户ID
     */
    private Integer user_id;

    /**
     * 面试ID
     */
    private Long interview_id;

    /**
     * 用户简历描述
     */
    private String user_description;

    /**
     * 是否完成，0-未完成，1-已完成
     */
    private Integer is_finish;

    /**
     * 创建时间
     */
    private Date create_time;

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
        AiInterview other = (AiInterview) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUser_id() == null ? other.getUser_id() == null : this.getUser_id().equals(other.getUser_id()))
            && (this.getInterview_id() == null ? other.getInterview_id() == null : this.getInterview_id().equals(other.getInterview_id()))
            && (this.getUser_description() == null ? other.getUser_description() == null : this.getUser_description().equals(other.getUser_description()))
            && (this.getIs_finish() == null ? other.getIs_finish() == null : this.getIs_finish().equals(other.getIs_finish()))
            && (this.getCreate_time() == null ? other.getCreate_time() == null : this.getCreate_time().equals(other.getCreate_time()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUser_id() == null) ? 0 : getUser_id().hashCode());
        result = prime * result + ((getInterview_id() == null) ? 0 : getInterview_id().hashCode());
        result = prime * result + ((getUser_description() == null) ? 0 : getUser_description().hashCode());
        result = prime * result + ((getIs_finish() == null) ? 0 : getIs_finish().hashCode());
        result = prime * result + ((getCreate_time() == null) ? 0 : getCreate_time().hashCode());
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
        sb.append(", interview_id=").append(interview_id);
        sb.append(", user_description=").append(user_description);
        sb.append(", is_finish=").append(is_finish);
        sb.append(", create_time=").append(create_time);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}