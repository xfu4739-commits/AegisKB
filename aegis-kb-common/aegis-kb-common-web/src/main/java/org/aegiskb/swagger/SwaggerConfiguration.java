package org.aegiskb.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI customOpenApi() {

        return new OpenAPI()
                .info(new Info()
                        .title("AegisKB")
                        .version("1.0")
                        .description("北京交通大学 · 企业知识库检索与问答")
                        .contact(new Contact()
                                .name("xfu-4739")
                                .email("1011571942@qq.com")
                        ));
    }
}
