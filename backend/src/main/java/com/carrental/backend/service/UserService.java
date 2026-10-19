package com.carrental.backend.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.carrental.backend.Dto.RegisterRequestDto;
import com.carrental.backend.Dto.UpdateUserRequestDto;
import com.carrental.backend.Dto.UserResponseDto;
import com.carrental.backend.Exceptions.EmailAlreadyExistsException;
import com.carrental.backend.Exceptions.UserNotFoundException;
import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.UserRepositary;

@Service
public class UserService {

    private final UserRepositary userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepositary userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        return convertToResponse(userRepository.save(user));
    }

    public UserResponseDto getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        return convertToResponse(user);
    }

    public UserResponseDto getMyProfileByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return convertToResponse(user);
    }

    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email))
                .getId();
    }

    public UserResponseDto updateMyProfile(Long userId, UpdateUserRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        user.setName(dto.getName());
        user.setPhone(dto.getPhone());

        return convertToResponse(userRepository.save(user));
    }

    public UserResponseDto updateMyProfileByEmail(String email, UpdateUserRequestDto dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        user.setName(dto.getName());
        user.setPhone(dto.getPhone());

        return convertToResponse(userRepository.save(user));
    }

    private UserResponseDto convertToResponse(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        return dto;
    }
}