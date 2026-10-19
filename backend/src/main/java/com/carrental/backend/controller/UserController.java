package com.carrental.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.backend.Dto.UpdateUserRequestDto;
import com.carrental.backend.Dto.UserResponseDto;
import com.carrental.backend.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users/me")
    public ResponseEntity<UserResponseDto> getMyProfile() {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(userService.getMyProfileByEmail(email));
    }

    @PutMapping("/users/me")
    public ResponseEntity<UserResponseDto> updateMyProfile(@Valid @RequestBody UpdateUserRequestDto request) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(userService.updateMyProfileByEmail(email, request));
    }

    private String getAuthenticatedEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal.toString();
    }
}
