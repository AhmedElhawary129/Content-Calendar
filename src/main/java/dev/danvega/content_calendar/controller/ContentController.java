package dev.danvega.content_calendar.controller;

import dev.danvega.content_calendar.model.Content;
import dev.danvega.content_calendar.model.Status;
import dev.danvega.content_calendar.model.User;
import dev.danvega.content_calendar.repository.ContentRepository;
import dev.danvega.content_calendar.service.AuthGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/content")
@CrossOrigin
public class ContentController {

//    private final ContentCollectionRepository repository;

//    private final ContentJdbcTemplateRepository repository;

    private final ContentRepository repository;
    private final AuthGuard authGuard;

    public ContentController(ContentRepository repository, AuthGuard authGuard) {
        this.repository = repository;
        this.authGuard = authGuard;
    }

    @GetMapping("")
    public List<Content> findAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Content findById(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("")
    public void create(@Valid @RequestBody Content content, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authGuard.getUserFromToken(authHeader);
        Content toSave = new Content(
                null,
                content.title(),
                content.desc(),
                content.status(),
                content.contentType(),
                LocalDateTime.now(),
                null,
                content.url(),
                user.id()
        );
        repository.save(toSave);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/{id}")
    public void update(@Valid @RequestBody Content content, @PathVariable Integer id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authGuard.getUserFromToken(authHeader);
        Content existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));

        if (!user.id().equals(existing.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own content");
        }

        Content updated = new Content(
                id,
                content.title(),
                content.desc(),
                content.status(),
                content.contentType(),
                existing.dateCreated(),
                LocalDateTime.now(),
                content.url(),
                existing.userId()
        );
        repository.save(updated);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authGuard.getUserFromToken(authHeader);

        Content existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found"));

        if (!user.id().equals(existing.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own content");
        }
        repository.deleteById(id);
    }

    @GetMapping("/filter/{keyword}")
    public List<Content> findByTitle(@PathVariable String keyword) {
        return repository.findAllByTitleContains(keyword);
    }

    @GetMapping("/filter/status/{status}")
    public List<Content> findByStatus(@PathVariable Status status) {
        return repository.listByStatus(status);
    }
}
