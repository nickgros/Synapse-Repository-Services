package org.sagebionetworks.repo.web.config;

import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocConfiguration;
import org.springdoc.core.SwaggerUiConfigParameters;
import org.springdoc.core.SwaggerUiConfigProperties;
import org.springdoc.core.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@EnableWebMvc
//@Import({SwaggerConfig.class,
//		SwaggerUiConfigProperties.class,
//		SwaggerUiConfigParameters.class,
//		SwaggerUiOAuthProperties.class,
//		SpringDocConfiguration.class,
//		SpringDocConfigProperties.class,
//		SpringDocWebMvcConfiguration.class,
//		MultipleOpenApiSupportConfiguration.class,
//		JacksonAutoConfiguration.class
//})
public class OpenApiConfiguration implements WebMvcConfigurer {

//	@Bean
	public GroupedOpenApi publicApi() {
		return GroupedOpenApi.builder().group("user").pathsToExclude("/api/v2/**").pathsToMatch("/api/v1/**").build();
	}

//	@Bean
	public GroupedOpenApi adminApi() {
		return GroupedOpenApi.builder().group("admin").pathsToExclude("/api/v1/**").pathsToMatch("/api/v2/**").build();
	}

}
