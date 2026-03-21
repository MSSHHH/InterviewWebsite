package com.yupi.mianshiya;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 主类（项目启动入口）
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
// todo 如需开启 Redis，须移除 exclude 中的内容
// @SpringBootApplication(exclude = {RedisAutoConfiguration.class})
@SpringBootApplication
@MapperScan("com.yupi.mianshiya.mapper")
@EnableScheduling
@ServletComponentScan
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MainApplication {

    public static void main(String[] args) {
        initSentinelSystemProperties();
        SpringApplication.run(MainApplication.class, args);
    }

    /**
     * Sentinel 心跳与命令端口优先读取 JVM/System 环境变量，
     * 避免在部分依赖组合下无法从 Spring 配置中正确感知 dashboard 地址。
     */
    private static void initSentinelSystemProperties() {
        setIfAbsent("csp.sentinel.dashboard.server", "SENTINEL_DASHBOARD", "localhost:8858");
        setIfAbsent("csp.sentinel.api.port", "SENTINEL_PORT", "8719");
    }

    private static void setIfAbsent(String systemKey, String envKey, String defaultValue) {
        String currentValue = System.getProperty(systemKey);
        if (currentValue != null && !currentValue.trim().isEmpty()) {
            return;
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            System.setProperty(systemKey, envValue);
            return;
        }
        System.setProperty(systemKey, defaultValue);
    }
}
