package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Mono;

/**
 * GraphQL Configuration
 *
 * This configuration enables CORS for GraphQL endpoint and GraphiQL UI
 */
@Configuration
public class GraphQLConfig implements WebMvcConfigurer {

    /**
     * Configure CORS for GraphQL endpoints
     * This allows GraphiQL UI to load external resources from CDN
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/graphql")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        registry.addMapping("/graphiql")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
