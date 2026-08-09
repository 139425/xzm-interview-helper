create table helper_user
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    user_id       int                                   not null comment '用户唯一标识',
    username      varchar(50)                           not null comment '用户名',
    password      varchar(255)                          not null comment '密码',
    user_type     varchar(20) default '正常用户'        not null comment '用户类型：管理员、正常用户',
    create_time   datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_id
        unique (user_id),
    constraint uk_username
        unique (username)
)
    comment '用户信息表' collate = utf8mb4_unicode_ci;

create index idx_user_type
    on helper_user (user_type);

