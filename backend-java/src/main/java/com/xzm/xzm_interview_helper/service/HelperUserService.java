package com.xzm.xzm_interview_helper.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.xzm.xzm_interview_helper.model.entity.HelperUser;

/**
* @author 34631
* @description 针对表【helper_user(用户信息表)】的数据库操作Service
* @createDate 2025-08-28 23:14:28
*/
public interface HelperUserService extends IService<HelperUser> {

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return 登录成功返回用户信息，失败返回null
     */
    HelperUser login(String username, String password);

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param captcha 验证码（前端校验，后端暂时忽略）
     * @return 注册成功返回用户信息，失败抛出异常
     */
    HelperUser register(String username, String password, String captcha);

}
