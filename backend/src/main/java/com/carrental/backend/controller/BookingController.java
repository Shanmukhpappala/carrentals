package com.carrental.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.backend.Dto.BookingRequestDto;
import com.carrental.backend.Dto.BookingResponseDto;
import com.carrental.backend.service.BookingService;
import com.carrental.backend.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;

    public BookingController(BookingService bookingService, UserService userService) {
        this.bookingService = bookingService;
        this.userService = userService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponseDto> createBooking(@Valid @RequestBody BookingRequestDto request) {
        Long userId = getAuthenticatedUserId();
        return ResponseEntity.status(201).body(bookingService.createBooking(userId, request));
    }

    @GetMapping("/bookings/my")
    public ResponseEntity<List<BookingResponseDto>> getMyBookings() {
        Long userId = getAuthenticatedUserId();
        return ResponseEntity.ok(bookingService.getMyBookings(userId));
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponseDto> getMyBooking(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();
        return ResponseEntity.ok(bookingService.getMyBooking(userId, id));
    }

    @PatchMapping("/bookings/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelMyBooking(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();
        return ResponseEntity.ok(bookingService.customerCancelBooking(userId, id));
    }

    @GetMapping("/admin/bookings")
    public ResponseEntity<List<BookingResponseDto>> getAllBookingsForAdmin() {
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @PatchMapping("/admin/bookings/{id}/approve")
    public ResponseEntity<BookingResponseDto> approveBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.approveBooking(id));
    }

    @PatchMapping("/admin/bookings/{id}/reject")
    public ResponseEntity<BookingResponseDto> rejectBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.rejectBooking(id));
    }

    @PatchMapping("/admin/bookings/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.adminCancelBooking(id));
    }

    @PatchMapping("/admin/bookings/{id}/pickup")
    public ResponseEntity<BookingResponseDto> pickupBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.pickupBooking(id));
    }

    @PatchMapping("/admin/bookings/{id}/complete")
    public ResponseEntity<BookingResponseDto> completeBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.completeBooking(id));
    }

    @PatchMapping("/admin/bookings/{id}/no-show")
    public ResponseEntity<BookingResponseDto> markNoShow(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.markNoShow(id));
    }

    private Long getAuthenticatedUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String email = principal instanceof UserDetails userDetails ? userDetails.getUsername() : principal.toString();
        return userService.getUserIdByEmail(email);
    }
}
