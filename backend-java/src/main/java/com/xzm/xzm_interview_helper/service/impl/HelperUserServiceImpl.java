package com.xzm.xzm_interview_helper.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xzm.xzm_interview_helper.mapper.HelperUserMapper;
import com.xzm.xzm_interview_helper.model.entity.HelperUser;
import com.xzm.xzm_interview_helper.service.HelperUserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Random;

/**
* @author 34631
* @description 针对表【helper_user(用户信息表)】的数据库操作Service实现
* @createDate 2025-08-28 23:14:28
*/
@Service
public class HelperUserServiceImpl extends ServiceImpl<HelperUserMapper, HelperUser>
implements HelperUserService {

    @Override
    public HelperUser login(String username, String password) {
        // 参数校验
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("用户名和密码不能为空");
        }
        
        // 查询用户
        QueryWrapper<HelperUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        HelperUser user = this.getOne(queryWrapper);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 验证密码（明文比较）
        if (!password.equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        return user;
    }

    @Override
    public HelperUser register(String username, String password, String captcha) {
        // 参数校验
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new RuntimeException("用户名和密码不能为空");
        }
        
        if (username.length() < 4 || username.length() > 20) {
            throw new RuntimeException("用户名长度必须在4-20字符之间");
        }
        
        if (password.length() < 4 || password.length() > 20) {
            throw new RuntimeException("密码长度必须在4-20字符之间");
        }
        
        // 检查用户名是否已存在
        QueryWrapper<HelperUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        HelperUser existUser = this.getOne(queryWrapper);
        
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 生成用户ID（随机生成1000-9999之间的数字）
        Integer userId = generateUserId();
        
        // 创建新用户
        HelperUser newUser = new HelperUser();
        newUser.setUser_id(userId);
        newUser.setUsername(username);
        newUser.setPassword(password); // 明文存储
        newUser.setUser_type("正常用户");
        newUser.setCreate_time(new Date());
        newUser.setUpdate_time(new Date());
        
        // 保存用户
        boolean saved = this.save(newUser);
        if (!saved) {
            throw new RuntimeException("注册失败");
        }
        
        return newUser;
    }
    
    /**
     * 生成唯一的用户ID
     */
    private Integer generateUserId() {
        Random random = new Random();
        Integer userId;
        
        do {
            // 生成1000-9999之间的随机数
            userId = 1000 + random.nextInt(9000);
            
            // 检查是否已存在
            QueryWrapper<HelperUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            HelperUser existUser = this.getOne(queryWrapper);
            
            if (existUser == null) {
                break;
            }
        } while (true);
        
        return userId;
    }
}
