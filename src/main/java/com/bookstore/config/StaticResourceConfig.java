package com.bookstore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imageRoot = Paths.get("images").toAbsolutePath().normalize();
        String imageLocation = imageRoot.toUri().toString();

        registry.addResourceHandler("/images/**")
                .addResourceLocations(imageLocation);
    }
}
