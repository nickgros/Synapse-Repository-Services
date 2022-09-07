package org.sagebionetworks.repo.web.config;

import static org.springdoc.core.Constants.API_DOCS_URL;
import static org.springdoc.core.Constants.DEFAULT_API_DOCS_URL;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springdoc.core.AbstractRequestService;
import org.springdoc.core.GenericResponseService;
import org.springdoc.core.OpenAPIService;
import org.springdoc.core.OperationService;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocProviders;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.RouterOperationCustomizer;
import org.springdoc.core.filters.OpenApiMethodFilter;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.OpenAPI;

@Component
public class OpenApiResource extends OpenApiWebMvcResource {

	public OpenApiResource(ObjectFactory<OpenAPIService> openAPIBuilderObjectFactory, AbstractRequestService requestBuilder, GenericResponseService responseBuilder, OperationService operationParser, Optional<List<OperationCustomizer>> operationCustomizers, Optional<List<OpenApiCustomiser>> openApiCustomisers, Optional<List<RouterOperationCustomizer>> routerOperationCustomizers, Optional<List<OpenApiMethodFilter>> methodFilters, SpringDocConfigProperties springDocConfigProperties, SpringDocProviders springDocProviders) {
		super("springdocDefault", openAPIBuilderObjectFactory, requestBuilder, responseBuilder, operationParser, operationCustomizers, openApiCustomisers, routerOperationCustomizers, methodFilters, springDocConfigProperties, springDocProviders);
	}


	@Operation(hidden = true)
	@GetMapping(value = "/v3/api-docs.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public OpenAPI openapiCustomJson(HttpServletRequest request, Locale locale) throws JsonProcessingException {
		String apiDocsUrl = "/v3/api-docs";
		this.calculateServerUrl(request, apiDocsUrl, locale);
		OpenAPI openAPI = this.getOpenApi(locale);
		return openAPI;
	}

}