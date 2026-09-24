package com.ordo.global.config;

import com.ordo.global.security.LoginUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    static {
        // @LoginUser 파라미터는 토큰에서 채우므로 Swagger 입력칸에서 숨긴다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);
    }

    // Swagger 우측 상단 Authorize 버튼에 accessToken 을 넣으면 모든 요청에 Bearer 헤더가 붙는다
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("ORDO API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
