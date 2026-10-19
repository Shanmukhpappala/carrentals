package com.carrental.backend.service;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.carrental.backend.Dto.LoginRequestDto;
import com.carrental.backend.Dto.LoginResponseDto;
import com.carrental.backend.Dto.RegisterRequestDto;
import com.carrental.backend.Dto.UserResponseDto;
import com.carrental.backend.Exceptions.EmailAlreadyExistsException;
import com.carrental.backend.Exceptions.UserNotFoundException;
import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.UserRepositary;

@Service
public class AuthService {

    private final UserRepositary userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepositary userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public UserResponseDto register(RegisterRequestDto dto) {
        String email = dto.getEmail() == null ? null : dto.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("User already exists with email: " + email);
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setRole("CUSTOMER");

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    public LoginResponseDto login(LoginRequestDto dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + userDetails.getUsername()));

        String token = jwtService.generateToken(userDetails);
        return new LoginResponseDto(token, user.getId(), user.getName(), user.getRole());
    }

    public LoginResponseDto loginAdmin(LoginRequestDto dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + userDetails.getUsername()));

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Customer accounts cannot access admin login");
        }

        String token = jwtService.generateToken(userDetails);
        return new LoginResponseDto(token, user.getId(), user.getName(), user.getRole());
    }

    private UserResponseDto mapToResponse(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        return dto;
    }
}
