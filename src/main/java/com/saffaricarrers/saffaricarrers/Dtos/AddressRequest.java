package com.saffaricarrers.saffaricarrers.Dtos;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class AddressRequest {

    private String addressType;

    private String fullName;

    private String address;


    private String city;


    private String state;


    private String country;

    private String pincode;

    private String mobile;

    private Boolean isDefault;
    private double latitude;
    private double longitude;
}