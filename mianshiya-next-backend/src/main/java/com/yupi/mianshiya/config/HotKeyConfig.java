package com.yupi.mianshiya.config;

import com.jd.platform.hotkey.client.ClientStarter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * hotkey 热 key 发现配置
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航</a>
 */
@Configuration
@ConfigurationProperties(prefix = "hotkey")
@Data
@Slf4j
public class HotKeyConfig {

    /**
     * Etcd 服务器完整地址
     */
    private String etcdServer = "http://127.0.0.1:2379";

    /**
     * 应用名称
     */
    private String appName = "mianshiya";

    /**
     * 本地缓存最大数量
     */
    private int caffeineSize = 10000;

    /**
     * 批量推送 key 的间隔时间
     */
    private long pushPeriod = 1000L;

    private volatile boolean started = false;

    /**
     * 初始化 hotkey
     */
    @PostConstruct
    public void initHotkey() {
        try {
            ClientStarter.Builder builder = new ClientStarter.Builder();
            ClientStarter starter = builder.setAppName(appName)
                    .setCaffeineSize(caffeineSize)
                    .setPushPeriod(pushPeriod)
                    .setEtcdServer(etcdServer)
                    .build();
            starter.startPipeline();
            started = true;
            log.info("hotkey client started, appName={}, etcdServer={}, caffeineSize={}", appName, etcdServer, caffeineSize);
        } catch (Exception e) {
            // 不让 HotKey 初始化失败影响主流程，降级走 Caffeine 本地缓存
            started = false;
            log.error("hotkey client start failed, fallback to local cache only", e);
        }
    }

}
