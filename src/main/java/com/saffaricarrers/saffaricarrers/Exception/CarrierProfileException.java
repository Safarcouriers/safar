package com.saffaricarrers.saffaricarrers.Exception;

public class CarrierProfileException extends RuntimeException {
    public CarrierProfileException(String message) {
        super(message);
    }

    public CarrierProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}