package org.sagebionetworks.repo.web.config;

import java.util.Collections;

import org.sagebionetworks.repo.web.controller.EntityController;
import org.sagebionetworks.repo.web.controller.ObjectTypeSerializer;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocConfiguration;
import org.springdoc.core.SpringDocUtils;
import org.springdoc.core.SwaggerUiConfigParameters;
import org.springdoc.core.SwaggerUiConfigProperties;
import org.springdoc.core.SwaggerUiOAuthProperties;
import org.springdoc.openapi.javadoc.SpringDocJavadocConfiguration;
import org.springdoc.webmvc.core.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

@Configuration
@EnableWebMvc
@ComponentScan(basePackageClasses = { SwaggerConfig.class,
		SpringDocJavadocConfiguration.class,
		SwaggerUiConfigProperties.class,
		SwaggerUiConfigParameters.class,
		SwaggerUiOAuthProperties.class,
		SpringDocConfiguration.class,
		SpringDocConfigProperties.class,
		SpringDocWebMvcConfiguration.class,
		MultipleOpenApiSupportConfiguration.class,
		JacksonAutoConfiguration.class
} )
public class RepoWebConfiguration implements WebMvcConfigurer {
	@Autowired
	private ObjectTypeSerializer exceptionSerializer;
		
	// Override the default ExceptionHandlerExceptionResolver used by spring so that we can customize the message converters and the content negotiation
	@Bean
	public ExceptionHandlerExceptionResolver controllerExceptionHandlerResolver() {
		ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
		
		resolver.setMessageConverters(Collections.singletonList(exceptionSerializer));
		resolver.setContentNegotiationManager(exceptionContentNegotiationManager());
		
		return resolver;
		
	}
	
	// The following beans are not exposed as they are used in place here
	
	private ContentNegotiationManager exceptionContentNegotiationManager() {
		return new ContentNegotiationManager(exceptionContentNegotiationStrategy());
	}
		
	private ContentNegotiationStrategy exceptionContentNegotiationStrategy() {
		return new ExceptionContentNegotiationStrategy(exceptionSerializer.getSupportedMediaTypes());
	}
	
	
}
