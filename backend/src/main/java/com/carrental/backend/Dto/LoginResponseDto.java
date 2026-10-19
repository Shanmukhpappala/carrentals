package com.carrental.backend.Dto;

public class LoginResponseDto {
	 private String token;
	 private Long userId;
	 private String name;
	 private String role;
	 public LoginResponseDto(String token, Long userId, String name, String role) {
		super();
		this.token = token;
		this.userId = userId;
		this.name = name;
		this.role = role;
	 }
	 public LoginResponseDto() {
		super();
	 }
	 public String getToken() {
		 return token;
	 }
	 public void setToken(String token) {
		 this.token = token;
	 }
	 public Long getUserId() {
		 return userId;
	 }
	 public void setUserId(Long userId) {
		 this.userId = userId;
	 }
	 public String getName() {
		 return name;
	 }
	 public void setName(String name) {
		 this.name = name;
	 }
	 public String getRole() {
		 return role;
	 }
	 public void setRole(String role) {
		 this.role = role;
	 }
	 
}
