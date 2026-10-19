package com.carrental.backend.Dto;

import java.math.BigDecimal;

public class CarResponseDto {
	 	private Long id;
	    private String brand;
	    private String model;
	    private String registrationNumber;
	    private Integer year;
	    private String fuelType;
	    private String transmission;
	    private Integer seatingCapacity;
	    private BigDecimal pricePerDay;
	    private String location;
	    private String status;
	    private String imageUrl;
	    private String description;
		public CarResponseDto(Long id, String brand, String model, String registrationNumber, Integer year,
				String fuelType, String transmission, Integer seatingCapacity, BigDecimal pricePerDay, String location,
				String status, String imageUrl, String description) {
			super();
			this.id = id;
			this.brand = brand;
			this.model = model;
			this.registrationNumber = registrationNumber;
			this.year = year;
			this.fuelType = fuelType;
			this.transmission = transmission;
			this.seatingCapacity = seatingCapacity;
			this.pricePerDay = pricePerDay;
			this.location = location;
			this.status = status;
			this.imageUrl = imageUrl;
			this.description = description;
		}
		public CarResponseDto() {
			super();
		}
		public Long getId() {
			return id;
		}
		public void setId(Long id) {
			this.id = id;
		}
		public String getBrand() {
			return brand;
		}
		public void setBrand(String brand) {
			this.brand = brand;
		}
		public String getModel() {
			return model;
		}
		public void setModel(String model) {
			this.model = model;
		}
		public String getRegistrationNumber() {
			return registrationNumber;
		}
		public void setRegistrationNumber(String registrationNumber) {
			this.registrationNumber = registrationNumber;
		}
		public Integer getYear() {
			return year;
		}
		public void setYear(Integer year) {
			this.year = year;
		}
		public String getFuelType() {
			return fuelType;
		}
		public void setFuelType(String fuelType) {
			this.fuelType = fuelType;
		}
		public String getTransmission() {
			return transmission;
		}
		public void setTransmission(String transmission) {
			this.transmission = transmission;
		}
		public Integer getSeatingCapacity() {
			return seatingCapacity;
		}
		public void setSeatingCapacity(Integer seatingCapacity) {
			this.seatingCapacity = seatingCapacity;
		}
		public BigDecimal getPricePerDay() {
			return pricePerDay;
		}
		public void setPricePerDay(BigDecimal pricePerDay) {
			this.pricePerDay = pricePerDay;
		}
		public String getLocation() {
			return location;
		}
		public void setLocation(String location) {
			this.location = location;
		}
		public String getStatus() {
			return status;
		}
		public void setStatus(String status) {
			this.status = status;
		}
		public String getImageUrl() {
			return imageUrl;
		}
		public void setImageUrl(String imageUrl) {
			this.imageUrl = imageUrl;
		}
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}
	    
	
}
