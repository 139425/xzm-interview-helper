package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 存储每一个问题，以及对应用户的回答，ai的打分，评价，参考回答，相关问题
 * @TableName ai_interview_question_reply
 */
@TableName(value ="ai_interview_question_reply")
@Data
public class AiInterviewQuestionReply implements Serializable {
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
     * 问题
     */
    private String question;

    /**
     * 用户回答
     */
    private String reply;

    /**
     * 问题类型
     */
    private String type;

    /**
     * 评分，1-10
     */
    private Integer score;

    /**
     * 评价
     */
    private String evaluation;

    /**
     * 参考回答方向
     */
    private String reference_answer_direction;

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
        AiInterviewQuestionReply other = (AiInterviewQuestionReply) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getUser_id() == null ? other.getUser_id() == null : this.getUser_id().equals(other.getUser_id()))
            && (this.getInterview_id() == null ? other.getInterview_id() == null : this.getInterview_id().equals(other.getInterview_id()))
            && (this.getQuestion() == null ? other.getQuestion() == null : this.getQuestion().equals(other.getQuestion()))
            && (this.getReply() == null ? other.getReply() == null : this.getReply().equals(other.getReply()))
            && (this.getType() == null ? other.getType() == null : this.getType().equals(other.getType()))
            && (this.getScore() == null ? other.getScore() == null : this.getScore().equals(other.getScore()))
            && (this.getEvaluation() == null ? other.getEvaluation() == null : this.getEvaluation().equals(other.getEvaluation()))
            && (this.getReference_answer_direction() == null ? other.getReference_answer_direction() == null : this.getReference_answer_direction().equals(other.getReference_answer_direction()))
            && (this.getCreate_time() == null ? other.getCreate_time() == null : this.getCreate_time().equals(other.getCreate_time()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getUser_id() == null) ? 0 : getUser_id().hashCode());
        result = prime * result + ((getInterview_id() == null) ? 0 : getInterview_id().hashCode());
        result = prime * result + ((getQuestion() == null) ? 0 : getQuestion().hashCode());
        result = prime * result + ((getReply() == null) ? 0 : getReply().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        result = prime * result + ((getScore() == null) ? 0 : getScore().hashCode());
        result = prime * result + ((getEvaluation() == null) ? 0 : getEvaluation().hashCode());
        result = prime * result + ((getReference_answer_direction() == null) ? 0 : getReference_answer_direction().hashCode());
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
        sb.append(", question=").append(question);
        sb.append(", reply=").append(reply);
        sb.append(", type=").append(type);
        sb.append(", score=").append(score);
        sb.append(", evaluation=").append(evaluation);
        sb.append(", reference_answer_direction=").append(reference_answer_direction);
        sb.append(", create_time=").append(create_time);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}