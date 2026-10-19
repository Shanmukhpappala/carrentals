package com.carrental.backend.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.backend.Dto.CarRequestDto;
import com.carrental.backend.Dto.CarResponseDto;
import com.carrental.backend.Dto.CarStatusDto;
import com.carrental.backend.Exceptions.CarNotAvailableException;
import com.carrental.backend.Exceptions.CarNotFoundException;
import com.carrental.backend.Exceptions.InvalidBookingException;
import com.carrental.backend.entity.Car;
import com.carrental.backend.repositary.BookingRepositary;
import com.carrental.backend.repositary.CarRepositary;

@Service
public class CarService {

    private final CarRepositary carRepository;
    private final BookingRepositary bookingRepository;

    public CarService(CarRepositary carRepository, BookingRepositary bookingRepository) {
        this.carRepository = carRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<CarResponseDto> getAvailableCars() {
        return carRepository.findByStatus("AVAILABLE")
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public List<CarResponseDto> getAvailableCars(LocalDate pickupDate, LocalDate returnDate) {
        validateDates(pickupDate, returnDate);

        return carRepository.findByStatus("AVAILABLE")
                .stream()
                .filter(car -> !bookingRepository.existsOverlappingBooking(car.getId(), pickupDate, returnDate))
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public CarResponseDto getCarById(Long carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException("Car not found with id: " + carId));
        if (!"AVAILABLE".equals(car.getStatus())) {
            throw new CarNotFoundException("Car not found with id: " + carId);
        }
        return convertToResponse(car);
    }

    public List<CarResponseDto> getAllCarsForAdmin() {
        return carRepository.findAll().stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public CarResponseDto getCarByIdForAdmin(Long carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException("Car not found with id: " + carId));
        return convertToResponse(car);
    }

    @Transactional
    public CarResponseDto addCar(CarRequestDto dto) {
        String registrationNumber = normalizeRegistrationNumber(dto.getRegistrationNumber());
        validateRegistrationNumber(registrationNumber, null);

        Car car = new Car();
        car.setBrand(dto.getBrand());
        car.setModel(dto.getModel());
        car.setRegistrationNumber(registrationNumber);
        car.setYear(dto.getYear());
        car.setFuelType(dto.getFuelType());
        car.setTransmission(dto.getTransmission());
        car.setSeatingCapacity(dto.getSeatingCapacity());
        car.setPricePerDay(dto.getPricePerDay());
        car.setLocation(dto.getLocation());
        car.setImageUrl(normalizeImageUrl(dto.getImageUrl()));
        car.setDescription(dto.getDescription());
        car.setStatus("AVAILABLE");

        return convertToResponse(carRepository.save(car));
    }

    @Transactional
    public CarResponseDto updateCar(Long carId, CarRequestDto dto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException("Car not found with id: " + carId));

        String newRegistrationNumber = normalizeRegistrationNumber(dto.getRegistrationNumber());
        validateRegistrationNumber(newRegistrationNumber, carId);

        car.setBrand(dto.getBrand());
        car.setModel(dto.getModel());
        car.setRegistrationNumber(newRegistrationNumber);
        car.setYear(dto.getYear());
        car.setFuelType(dto.getFuelType());
        car.setTransmission(dto.getTransmission());
        car.setSeatingCapacity(dto.getSeatingCapacity());
        car.setPricePerDay(dto.getPricePerDay());
        car.setLocation(dto.getLocation());
        if (dto.getImageUrl() != null) {
            car.setImageUrl(normalizeImageUrl(dto.getImageUrl()));
        }
        car.setDescription(dto.getDescription());

        return convertToResponse(carRepository.save(car));
    }

    @Transactional
    public CarResponseDto changeCarStatus(Long carId, CarStatusDto dto) {
        Car car = carRepository.findByIdForUpdate(carId)
                .orElseThrow(() -> new CarNotFoundException("Car not found with id: " + carId));

        String status = dto.getStatus() == null ? null : dto.getStatus().trim().toUpperCase();
        if (!"AVAILABLE".equals(status) && !"INACTIVE".equals(status) && !"MAINTENANCE".equals(status)) {
            throw new InvalidBookingException("Invalid car status: " + status);
        }

        if ("AVAILABLE".equals(car.getStatus()) && !"AVAILABLE".equals(status)
                && bookingRepository.existsConfirmedOrActiveBookingForCar(carId)) {
            throw new CarNotAvailableException("Car cannot be made unavailable while it has confirmed or active bookings");
        }

        car.setStatus(status);
        return convertToResponse(carRepository.save(car));
    }

    private void validateDates(LocalDate pickupDate, LocalDate returnDate) {
        if (pickupDate == null || returnDate == null) {
            throw new InvalidBookingException("Pickup date and return date are required");
        }

        if (pickupDate.isBefore(LocalDate.now())) {
            throw new InvalidBookingException("Pickup date cannot be in the past");
        }

        if (returnDate.isBefore(pickupDate)) {
            throw new InvalidBookingException("Return date cannot be before pickup date");
        }
    }

    private void validateRegistrationNumber(String registrationNumber, Long carIdToIgnore) {
        if (carRepository.existsByNormalizedRegistrationNumber(registrationNumber, carIdToIgnore)) {
            throw new CarNotAvailableException("Car already exists with registration number: " + registrationNumber);
        }
    }

    private String normalizeRegistrationNumber(String registrationNumber) {
        if (registrationNumber == null) {
            return null;
        }
        return registrationNumber.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }

    private CarResponseDto convertToResponse(Car car) {
        CarResponseDto dto = new CarResponseDto();
        dto.setId(car.getId());
        dto.setBrand(car.getBrand());
        dto.setModel(car.getModel());
        dto.setRegistrationNumber(car.getRegistrationNumber());
        dto.setYear(car.getYear());
        dto.setFuelType(car.getFuelType());
        dto.setTransmission(car.getTransmission());
        dto.setSeatingCapacity(car.getSeatingCapacity());
        dto.setPricePerDay(car.getPricePerDay());
        dto.setLocation(car.getLocation());
        dto.setStatus(car.getStatus());
        dto.setImageUrl(car.getImageUrl());
        dto.setDescription(car.getDescription());
        return dto;
    }
}

