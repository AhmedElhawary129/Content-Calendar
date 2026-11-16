package dev.danvega.content_calendar.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// The JSON body the client sends to log in
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
