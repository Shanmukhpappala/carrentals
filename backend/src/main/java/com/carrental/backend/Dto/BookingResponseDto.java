package com.carrental.backend.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BookingResponseDto {
	private Long id;

    private Long userId;
    private String userName;

    private Long carId;
    private String carBrand;
    private String carModel;

    private LocalDate pickupDate;
    private LocalDate returnDate;

    private Integer numberOfDays;

    private BigDecimal pricePerDay;
    private BigDecimal totalAmount;

    private String status;
    private LocalDate createdAt;
	public BookingResponseDto(Long id, Long userId, String userName, Long carId, String carBrand, String carModel,
			LocalDate pickupDate, LocalDate returnDate, Integer numberOfDays, BigDecimal pricePerDay,
			BigDecimal totalAmount, String status, LocalDate createdAt) {
		super();
		this.id = id;
		this.userId = userId;
		this.userName = userName;
		this.carId = carId;
		this.carBrand = carBrand;
		this.carModel = carModel;
		this.pickupDate = pickupDate;
		this.returnDate = returnDate;
		this.numberOfDays = numberOfDays;
		this.pricePerDay = pricePerDay;
		this.totalAmount = totalAmount;
		this.status = status;
		this.createdAt = createdAt;
	}
	public BookingResponseDto() {
		super();
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Long getUserId() {
		return userId;
	}
	public void setUserId(Long userId) {
		this.userId = userId;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public Long getCarId() {
		return carId;
	}
	public void setCarId(Long carId) {
		this.carId = carId;
	}
	public String getCarBrand() {
		return carBrand;
	}
	public void setCarBrand(String carBrand) {
		this.carBrand = carBrand;
	}
	public String getCarModel() {
		return carModel;
	}
	public void setCarModel(String carModel) {
		this.carModel = carModel;
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
	public Integer getNumberOfDays() {
		return numberOfDays;
	}
	public void setNumberOfDays(Integer numberOfDays) {
		this.numberOfDays = numberOfDays;
	}
	public BigDecimal getPricePerDay() {
		return pricePerDay;
	}
	public void setPricePerDay(BigDecimal pricePerDay) {
		this.pricePerDay = pricePerDay;
	}
	public BigDecimal getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public LocalDate getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(LocalDate createdAt) {
		this.createdAt = createdAt;
	}
    
}
