package com.icepark;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟：完整 Spring 上下文（含所有 Controller/Service/Repository、Redis 连接工厂懒加载）
 * 可以启动，全部 JPA 实体能在数据库上完成 DDL 建表（含唯一索引/条件查询）。
 */
@SpringBootTest
class ApplicationContextSmokeTest {

    @Test
    void contextLoads() {
    }
}
