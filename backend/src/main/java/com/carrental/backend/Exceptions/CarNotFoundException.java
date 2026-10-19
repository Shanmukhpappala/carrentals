package com.carrental.backend.Exceptions;

public class CarNotFoundException extends RuntimeException {
	public CarNotFoundException(String message) {
        super(message);
    }
}
