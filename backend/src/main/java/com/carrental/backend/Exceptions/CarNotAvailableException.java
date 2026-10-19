package com.carrental.backend.Exceptions;

public class CarNotAvailableException extends RuntimeException {

    public CarNotAvailableException(String message) {
        super(message);
    }
}
