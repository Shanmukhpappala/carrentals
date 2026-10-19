package com.carrental.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.carrental.backend.Dto.CarRequestDto;
import com.carrental.backend.Dto.CarResponseDto;
import com.carrental.backend.Dto.CarStatusDto;
import com.carrental.backend.Exceptions.CarNotAvailableException;
import com.carrental.backend.Exceptions.CarNotFoundException;
import com.carrental.backend.entity.Car;
import com.carrental.backend.repositary.BookingRepositary;
import com.carrental.backend.repositary.CarRepositary;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock
    private CarRepositary carRepository;

    @Mock
    private BookingRepositary bookingRepository;

    private CarService carService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        carService = new CarService(carRepository, bookingRepository);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void addCar_shouldPersistAndReturnImageUrl() {
        CarRequestDto request = validRequest();
        request.setImageUrl("  https://images.example/car.jpg  ");
        when(carRepository.existsByNormalizedRegistrationNumber("TEST-123", null)).thenReturn(false);
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> {
            Car car = invocation.getArgument(0);
            car.setId(1L);
            return car;
        });

        CarResponseDto response = carService.addCar(request);

        assertThat(response.getImageUrl()).isEqualTo("https://images.example/car.jpg");
    }

    @Test
    void addCar_shouldAllowMissingImageUrl() {
        when(carRepository.existsByNormalizedRegistrationNumber("TEST-123", null)).thenReturn(false);
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarResponseDto response = carService.addCar(validRequest());

        assertThat(response.getImageUrl()).isNull();
    }

    @Test
    void updateCar_shouldReplaceOrClearImageUrl() {
        Car car = existingCar("https://images.example/old.jpg");
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(carRepository.existsByNormalizedRegistrationNumber("TEST-123", 1L)).thenReturn(false);
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CarRequestDto request = validRequest();
        request.setImageUrl("https://images.example/new.jpg");

        assertThat(carService.updateCar(1L, request).getImageUrl()).isEqualTo("https://images.example/new.jpg");

        request.setImageUrl("");
        assertThat(carService.updateCar(1L, request).getImageUrl()).isNull();
    }

    @Test
    void updateCar_shouldPreserveImageWhenRequestOmitsIt() {
        Car car = existingCar("https://images.example/current.jpg");
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(carRepository.existsByNormalizedRegistrationNumber("TEST-123", 1L)).thenReturn(false);
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarResponseDto response = carService.updateCar(1L, validRequest());

        assertThat(response.getImageUrl()).isEqualTo("https://images.example/current.jpg");
    }

    @Test
    void adminDirectoryIncludesAvailableInactiveAndMaintenanceCars() {
        when(carRepository.findAll()).thenReturn(List.of(
                carWithStatus("AVAILABLE"), carWithStatus("INACTIVE"), carWithStatus("MAINTENANCE")));

        assertThat(carService.getAllCarsForAdmin()).extracting(CarResponseDto::getStatus)
                .containsExactly("AVAILABLE", "INACTIVE", "MAINTENANCE");
    }

    @Test
    void adminCanLoadUnavailableCarDetails() {
        when(carRepository.findById(1L)).thenReturn(Optional.of(carWithStatus("MAINTENANCE")));

        assertThat(carService.getCarByIdForAdmin(1L).getStatus()).isEqualTo("MAINTENANCE");
    }

    @Test
    void publicDetailsDoNotExposeUnavailableCar() {
        when(carRepository.findById(1L)).thenReturn(Optional.of(carWithStatus("MAINTENANCE")));

        assertThatThrownBy(() -> carService.getCarById(1L))
                .isInstanceOf(CarNotFoundException.class);
    }

    @Test
    void cannotDeactivateCarWithConfirmedOrActiveBookings() {
        when(carRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(carWithStatus("AVAILABLE")));
        when(bookingRepository.existsConfirmedOrActiveBookingForCar(1L)).thenReturn(true);

        assertThatThrownBy(() -> carService.changeCarStatus(1L, new CarStatusDto("MAINTENANCE")))
                .isInstanceOf(CarNotAvailableException.class)
                .hasMessageContaining("confirmed or active bookings");
        verify(carRepository, never()).save(any(Car.class));
    }

    @Test
    void canDeactivateCarWhenOnlyRequestedBookingsExist() {
        Car car = carWithStatus("AVAILABLE");
        when(carRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsConfirmedOrActiveBookingForCar(1L)).thenReturn(false);
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(carService.changeCarStatus(1L, new CarStatusDto("INACTIVE")).getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void imageUrlValidation_shouldAllowHttpHttpsAndEmptyButRejectUnsafeSchemes() {
        CarRequestDto request = validRequest();
        assertThat(validator.validate(request)).isEmpty();

        request.setImageUrl("https://images.example/car.jpg");
        assertThat(validator.validate(request)).isEmpty();

        request.setImageUrl("http://images.example/car.jpg");
        assertThat(validator.validate(request)).isEmpty();

        request.setImageUrl("javascript:alert(1)");
        assertThat(validator.validate(request)).anyMatch(
                violation -> violation.getMessage().contains("absolute HTTP or HTTPS"));
    }

    private CarRequestDto validRequest() {
        return new CarRequestDto("Toyota", "Corolla", "TEST-123", 2025, "Hybrid", "Automatic", 5,
                new BigDecimal("75.00"), "City", "Test vehicle");
    }

    private Car existingCar(String imageUrl) {
        Car car = new Car();
        car.setId(1L);
        car.setBrand("Toyota");
        car.setModel("Corolla");
        car.setRegistrationNumber("TEST-123");
        car.setYear(2025);
        car.setFuelType("Hybrid");
        car.setTransmission("Automatic");
        car.setSeatingCapacity(5);
        car.setPricePerDay(new BigDecimal("75.00"));
        car.setLocation("City");
        car.setStatus("AVAILABLE");
        car.setImageUrl(imageUrl);
        car.setDescription("Test vehicle");
        return car;
    }

    private Car carWithStatus(String status) {
        Car car = existingCar(null);
        car.setStatus(status);
        return car;
    }
}
