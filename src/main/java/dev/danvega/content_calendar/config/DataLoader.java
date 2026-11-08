package dev.danvega.content_calendar.config;

import dev.danvega.content_calendar.model.Content;
import dev.danvega.content_calendar.repository.ContentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

//@Profile("!dev")
@Component
public class DataLoader implements CommandLineRunner {

    private final ContentRepository repository;
    private final ObjectMapper objectMapper;

    public DataLoader(ContentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) {
            try(InputStream inputStream = TypeReference.class.getResourceAsStream("/data/content.json")) {
                repository.saveAll(objectMapper.readValue(inputStream, new TypeReference<List<Content>>() {}));
            }
        }
    }
}
