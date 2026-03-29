package com.saffaricarrers.saffaricarrers.Dtos;


import com.saffaricarrers.saffaricarrers.Entity.Issue;
import lombok.*;

import java.time.LocalDateTime;

public class IssueDTO {

    // ── Request ──────────────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String firebaseUid;
        private String category;
        private String description;
        private Issue.Priority priority;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateStatusRequest {
        private Issue.Status status;
        private String adminNotes;
        private String resolvedBy;
    }

    // ── Response ─────────────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private Long id;
        private String firebaseUid;
        private String category;
        private String description;
        private Issue.Priority priority;
        private Issue.Status status;
        private String adminNotes;
        private String resolvedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Issue issue) {
            return Response.builder()
                    .id(issue.getId())
                    .firebaseUid(issue.getFirebaseUid())
                    .category(issue.getCategory())
                    .description(issue.getDescription())
                    .priority(issue.getPriority())
                    .status(issue.getStatus())
                    .adminNotes(issue.getAdminNotes())
                    .resolvedBy(issue.getResolvedBy())
                    .createdAt(issue.getCreatedAt())
                    .updatedAt(issue.getUpdatedAt())
                    .build();
        }
    }

    // ── API Wrapper ───────────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null);
        }
    }
}