package com.carrental.backend.Dto;

import jakarta.validation.constraints.NotBlank;

public class CarStatusDto {
    @NotBlank(message = "Status is required")
    private String status;

    public CarStatusDto(String status) {
        this.status = status;
    }

    public CarStatusDto() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
