package dev.danvega.content_calendar.dto;

import dev.danvega.content_calendar.model.User;

public record UserResponse(
        Integer id,
        String firstName,
        String lastName,
        String email
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.firstName(), user.lastName(), user.email());
    }
}
