package com.carrental.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.backend.Dto.LoginRequestDto;
import com.carrental.backend.Dto.LoginResponseDto;
import com.carrental.backend.Dto.RegisterRequestDto;
import com.carrental.backend.Dto.UserResponseDto;
import com.carrental.backend.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/admin/auth/login")
    public ResponseEntity<LoginResponseDto> loginAdmin(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.loginAdmin(request));
    }
}
