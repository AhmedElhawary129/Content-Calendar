package dev.danvega.content_calendar.dto;

public record LoginResponse(String token, UserResponse user) {
}