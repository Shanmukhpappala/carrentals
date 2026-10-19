package com.carrental.backend.Dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Phone is required")
    private String phone;

    public UpdateUserRequestDto() {
    }

    public UpdateUserRequestDto(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
