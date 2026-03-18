drop database if exists medrec;
create database medrec;
use medrec;

drop table if exists t_user;
drop table if exists t_agent;
drop table if exists t_vector;
drop table if exists t_knowledge;
drop table if exists t_agent_vector;



create table t_user
(
    id            int primary key auto_increment comment '主键id',
    account       varchar(50) not null unique comment '账号',
    username      varchar(50) not null comment '用户名',
    password      varchar(50) not null comment '密码',
    register_time timestamp   not null default current_timestamp() comment '注册时间',
    login_time    timestamp            default null comment '登录时间'
) comment ='用户表';

create table t_agent
(
    id          int primary key auto_increment comment '主键id',
    name        varchar(50) not null comment '名称',
    description varchar(200) comment '描述',
    create_by   int         not null comment '创建人'
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
    create_by   int          not null comment '创建人',
    create_time timestamp    not null default current_timestamp() comment '创建时间'
) comment ='知识库表';

create table t_agent_vector
(
    id          int primary key auto_increment comment '主键id',
    agent_id    int         not null comment 'ai智能体id',
    vector_id   int         not null comment '向量id'
);

-- 1. t_user 表添加测试数据
insert into t_user (account, username, password, register_time, login_time)
values ('admin', '管理员', '123456', now(), now());

-- 2. t_agent 表添加测试数据
insert into t_agent (name, description, create_by)
values ('医疗问答助手', '专门回答医疗健康相关问题的AI助手', 1);

-- 3. t_vector 表添加测试数据
insert into t_vector (name, description, dim, index_name, prefix, create_by, create_time)
values ('医疗知识库', '包含常见疾病和药物信息的向量库', 1536, 'med_idx', 'med_', 1, now());

-- 4. t_knowledge 表添加测试数据
insert into t_knowledge (name, path, vector_id, create_by, create_time)
values ('常见疾病手册.pdf', '/data/knowledge/常见疾病手册.pdf', 1, 1, now());

-- 5. t_agent_vector 表添加测试数据
insert into t_agent_vector (agent_id, vector_id)
values (1, 1);