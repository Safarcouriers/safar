package com.saffaricarrers.saffaricarrers.N1.dto;

import jakarta.validation.constraints.*;

import lombok.*;

public class N1InfluencerDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotBlank
        private String name;

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message = "Code may contain only letters, numbers, _ and -"
        )
        private String code;

        private String email;

        @Min(0)
        @Max(100)
        private Integer commissionPercent = 20;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AttributeRequest {

        @NotBlank
        private String influencerCode;

        private String source;
    }

    @Getter
    @Builder
    public static class ApiResponse {

        private boolean success;
        private String message;
        private Object data;

        public static ApiResponse success(
                String message,
                Object data
        ) {
            return ApiResponse.builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static ApiResponse error(
                String message
        ) {
            return ApiResponse.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}