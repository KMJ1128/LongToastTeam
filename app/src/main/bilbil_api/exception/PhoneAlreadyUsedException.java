package com.longtoast.bilbil_api.exception;

public class PhoneAlreadyUsedException extends RuntimeException {
    public PhoneAlreadyUsedException(String message) {
        super(message);
    }
}
