package com.carrental.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.backend.Dto.CarRequestDto;
import com.carrental.backend.Dto.CarResponseDto;
import com.carrental.backend.Dto.CarStatusDto;
import com.carrental.backend.Exceptions.InvalidBookingException;
import com.carrental.backend.service.CarService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping("/cars")
    public ResponseEntity<List<CarResponseDto>> getCars(
            @RequestParam(required = false) LocalDate pickupDate,
            @RequestParam(required = false) LocalDate returnDate) {

        if ((pickupDate == null) != (returnDate == null)) {
            throw new InvalidBookingException("Pickup date and return date must both be provided together");
        }

        if (pickupDate != null && returnDate != null) {
            return ResponseEntity.ok(carService.getAvailableCars(pickupDate, returnDate));
        }

        return ResponseEntity.ok(carService.getAvailableCars());
    }

    @GetMapping("/cars/{id}")
    public ResponseEntity<CarResponseDto> getCarById(@PathVariable Long id) {
        return ResponseEntity.ok(carService.getCarById(id));
    }

    @GetMapping("/admin/cars")
    public ResponseEntity<List<CarResponseDto>> getAllCarsForAdmin() {
        return ResponseEntity.ok(carService.getAllCarsForAdmin());
    }

    @GetMapping("/admin/cars/{id}")
    public ResponseEntity<CarResponseDto> getCarByIdForAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(carService.getCarByIdForAdmin(id));
    }

    @PostMapping("/admin/cars")
    public ResponseEntity<CarResponseDto> addCar(@Valid @RequestBody CarRequestDto request) {
        return ResponseEntity.status(201).body(carService.addCar(request));
    }

    @PutMapping("/admin/cars/{id}")
    public ResponseEntity<CarResponseDto> updateCar(@PathVariable Long id, @Valid @RequestBody CarRequestDto request) {
        return ResponseEntity.ok(carService.updateCar(id, request));
    }

    @PatchMapping("/admin/cars/{id}/status")
    public ResponseEntity<CarResponseDto> changeCarStatus(@PathVariable Long id, @Valid @RequestBody CarStatusDto request) {
        return ResponseEntity.ok(carService.changeCarStatus(id, request));
    }
}
