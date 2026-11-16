package config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import utils.JsonFileUtil;

@Configuration
public class FileStorageConfiguration implements WebMvcConfigurer {

    public FileStorageConfiguration(@Value("${cinema.data-dir:data}") String directory) {
        JsonFileUtil.setDataDirectory(directory);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path posterDir = Path.of("data", "posters").toAbsolutePath().normalize();
        registry.addResourceHandler("/posters/**")
                .addResourceLocations(posterDir.toUri().toString().replaceFirst("/?$", "/"));

        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
