package dev.danvega.content_calendar.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

public record Content(
        @Id
        Integer id,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Column(value = "description")
        String desc,

        @NotNull(message = "Status is required")
        Status status,

        @NotNull(message = "Content type is required")
        Type contentType,

        LocalDateTime dateCreated,
        LocalDateTime dateUpdated,
        String url,

        Integer userId
) {
}