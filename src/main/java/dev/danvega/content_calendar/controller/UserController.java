package dev.danvega.content_calendar.controller;

import dev.danvega.content_calendar.dto.LoginRequest;
import dev.danvega.content_calendar.dto.LoginResponse;
import dev.danvega.content_calendar.dto.RegisterRequest;
import dev.danvega.content_calendar.dto.UserResponse;
import dev.danvega.content_calendar.model.User;
import dev.danvega.content_calendar.repository.UserRepository;
import dev.danvega.content_calendar.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserController(UserRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @GetMapping("")
    public List<UserResponse> findAll() {
        return repository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = new User(
            null,
            request.firstName(),
            request.lastName(),
            email,
            passwordEncoder.encode(request.password()),
            LocalDateTime.now(),
            null
        );

        User saved = repository.save(user);
        return UserResponse.from(saved);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, UserResponse.from(user));
    }
}
