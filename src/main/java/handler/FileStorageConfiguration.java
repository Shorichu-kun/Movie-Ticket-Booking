package handler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import utils.JsonFileUtil;

@Configuration
public class FileStorageConfiguration {
    public FileStorageConfiguration(@Value("${cinema.data-dir:data}") String directory) {
        JsonFileUtil.setDataDirectory(directory);
    }
}
