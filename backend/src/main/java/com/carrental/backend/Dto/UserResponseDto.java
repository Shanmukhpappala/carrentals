package com.carrental.backend.Dto;

public class UserResponseDto {
	 private Long id;
	 private String name;
	 private String email;
	 private String phone;
	 private String role;
	 public UserResponseDto(Long id, String name, String email, String phone, String role) {
		super();
		this.id = id;
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.role = role;
	 }
	 public UserResponseDto() {
		super();
	 }
	 public Long getId() {
		 return id;
	 }
	 public void setId(Long id) {
		 this.id = id;
	 }
	 public String getName() {
		 return name;
	 }
	 public void setName(String name) {
		 this.name = name;
	 }
	 public String getEmail() {
		 return email;
	 }
	 public void setEmail(String email) {
		 this.email = email;
	 }
	 public String getPhone() {
		 return phone;
	 }
	 public void setPhone(String phone) {
		 this.phone = phone;
	 }
	 public String getRole() {
		 return role;
	 }
	 public void setRole(String role) {
		 this.role = role;
	 }
	 
}
