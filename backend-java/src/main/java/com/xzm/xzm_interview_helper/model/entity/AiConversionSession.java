package com.xzm.xzm_interview_helper.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI会话摘要实体类
 * 用于存储每个会话的基本信息，优化历史记录查询性能
 */
@Data
@TableName("ai_conversion_session")
public class AiConversionSession {
    
    /**
     * 主键ID，自动生成
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 会话记忆ID
     */
    private Long memoryId;
    
    /**
     * 会话标题（第一次对话的用户输入）
     */
    private String title;
    
    /**
     * 创建时间
     */
    private Date createTime;
}
