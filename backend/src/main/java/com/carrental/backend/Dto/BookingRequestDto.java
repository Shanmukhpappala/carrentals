package com.carrental.backend.Dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public class BookingRequestDto {
    @NotNull(message = "Car ID is required")
    private Long carId;

    @NotNull(message = "Pickup date is required")
    private LocalDate pickupDate;

    @NotNull(message = "Return date is required")
    private LocalDate returnDate;

    public BookingRequestDto(Long carId, LocalDate pickupDate, LocalDate returnDate) {
        this.carId = carId;
        this.pickupDate = pickupDate;
        this.returnDate = returnDate;
    }

    public BookingRequestDto() {
    }

    public Long getCarId() {
        return carId;
    }

    public void setCarId(Long carId) {
        this.carId = carId;
    }

    public LocalDate getPickupDate() {
        return pickupDate;
    }

    public void setPickupDate(LocalDate pickupDate) {
        this.pickupDate = pickupDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }
}
