package com.ppxb;

import cn.hutool.core.util.URLUtil;
import com.ppxb.common.core.utils.NetUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;

/**
 * 启动程序
 *
 * @author ppxb
 * @since 1.0.0
 */
@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class OreoApplication implements ApplicationRunner {

    private final ServerProperties serverProperties;

    public static void main(String[] args) {
        SpringApplication.run(OreoApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        String host = NetUtils.getLocalhostStr();
        Integer port = serverProperties.getPort();
        String contextPath = serverProperties.getServlet().getContextPath();
        String baseUrl = URLUtil.normalize("%s:%s%s".formatted(host, port, contextPath));
        log.info("-----------------------------------------------------");
        log.info("server started successfully.");
        log.info("Spring Boot: v{}", SpringBootVersion.getVersion());
        log.info("服务地址: {}", baseUrl);
        log.info("接口文档: {}/v3/api-docs", baseUrl);
    }
}
