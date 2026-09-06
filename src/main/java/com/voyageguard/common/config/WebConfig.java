package com.voyageguard.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 개발용 CORS 허용. voyageguard-front(Vite, 보통 5173)나 file:// 로 여는 테스트 페이지가
 * 이 백엔드(8080)를 직접 호출할 수 있게 한다. 운영 배포 시에는 실제 프론트 도메인으로 좁혀야 함.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*");
    }
}
