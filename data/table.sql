drop database if exists medrec;
create database medrec;

use medrec;

drop table if exists t_user;
drop table if exists t_agent;
drop table if exists t_vector;
drop table if exists t_knowledge;
drop table if exists t_agent_vector;

DROP TABLE IF EXISTS SPRING_AI_CHAT_MEMORY;



create table t_user
(
    id            int primary key auto_increment comment '主键id',
    account       varchar(50) not null unique comment '账号',
    username      varchar(50) not null comment '用户名',
    password      varchar(60) not null comment '密码',
    register_time timestamp   not null default current_timestamp() comment '注册时间',
    login_time    timestamp            default null comment '登录时间'
) comment ='用户表';

create table t_agent
(
    id          int primary key auto_increment comment '主键id',
    name        varchar(50) not null comment '名称',
    description varchar(200) comment '描述',
    prompt       varchar(200) comment '提示语',
    temperature  decimal(3,2)  comment '温度',
    max_message  int          not null comment '最大消息数',
    top_k         int          not null comment '文档数',
    similarity   decimal(3,2)  comment '相似度',
    create_by   int         not null comment '创建人',
    create_time timestamp   not null default current_timestamp() comment '创建时间',
    constraint temperature_constraint check ( temperature >= 0 and temperature <= 1 )
) comment ='ai智能体';

create table t_vector
(
    id          int primary key auto_increment comment '主键id',
    name        varchar(50) not null comment '知识库名称',
    description varchar(200) comment '描述',
    dim         int         not null comment '向量维度',
    index_name  varchar(50) not null comment '索引名称',
    prefix      varchar(50) not null comment '前缀',
    create_by   int         not null comment '创建人',
    create_time timestamp   not null default current_timestamp() comment '创建时间'
) comment ='向量库表';

create table t_knowledge
(
    id          int primary key auto_increment comment '主键id',
    name        varchar(50)  not null comment '源文件名称',
    path        varchar(200) not null comment '源文件路径',
    vector_id   int          not null comment '向量id',
    chunk       int          not null comment '分块数',
    create_by   int          not null comment '创建人',
    create_time timestamp    not null default current_timestamp() comment '创建时间'
) comment ='知识库表';

create table t_agent_vector
(
    id          int primary key auto_increment comment '主键id',
    agent_id    int         not null comment 'ai智能体id',
    vector_id   int         not null comment '向量id'
);

CREATE TABLE SPRING_AI_CHAT_MEMORY
(
    `id`              bigint       NOT NULL AUTO_INCREMENT,
    `conversation_id` varchar(255) NOT NULL,
    `content`         text         NOT NULL,
    `type`            varchar(20)  NOT NULL,
    `timestamp`       timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;