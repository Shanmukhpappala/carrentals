package com.carrental.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Booking {
	 	@Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    private LocalDate pickupDate;

	    private LocalDate returnDate;

	    private BigDecimal totalAmount;    
	    private String status;
	    private LocalDate createdAt;

	    @ManyToOne
	    private User user;

	    @ManyToOne
	    private Car car; 

		public Booking(LocalDate pickupDate, LocalDate returnDate, BigDecimal totalAmount, String status,
				LocalDate createdAt, User user, Car car) {
			super();
			this.pickupDate = pickupDate;
			this.returnDate = returnDate;
			this.totalAmount = totalAmount;
			this.status = status;
			this.createdAt = createdAt;
			this.user = user;
			this.car = car;
		}

		public Booking() {
			super();
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
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

		public User getUser() {
			return user;
		}

		public void setUser(User user) {
			this.user = user;
		}

		public Car getCar() {
			return car;
		}

		public void setCar(Car car) {
			this.car = car;
		}
	    
}
