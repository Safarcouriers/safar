package com.saffaricarrers.saffaricarrers.Responses;

import java.util.ArrayList;
import java.util.List;

public class ApiResponse1<T> {
    private String status;  // "success" or "error"
    private String message;
    private T data;
    private List<String> errors;

    public ApiResponse1(String status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.errors = new ArrayList<>();
    }

    // Static factory methods
    public static <T> ApiResponse1<T> success(String message, T data) {
        return new ApiResponse1<>("success", message, data);
    }

    public static <T> ApiResponse1<T> error(String message) {
        return new ApiResponse1<>("error", message, null);
    }

    public static <T> ApiResponse1<T> error(String message, List<String> errors) {
        ApiResponse1<T> response = new ApiResponse1<>("error", message, null);
        response.setErrors(errors);
        return response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
// Getters and setters
}
