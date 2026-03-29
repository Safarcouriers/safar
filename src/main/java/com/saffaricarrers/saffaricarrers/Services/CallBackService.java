package com.saffaricarrers.saffaricarrers.Services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class CallBackService {

    @Autowired
    private AmazonS3 amazonS3;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    // List of allowed image types
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    // Maximum file size (5MB)
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    public String uploadToS3(InputStream inputStream, String contentType, long contentLength) throws IOException {
        validateFileType(contentType);
        validateFileSize(contentLength);

        String key = generateUniqueKey();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);

        amazonS3.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
        return generateS3Url(key);
    }

    // New method to handle MultipartFile uploads for documents
    public String uploadDocumentToS3(MultipartFile file, String documentType) throws IOException {
        if (file.isEmpty()) {
            throw new S3UploadException("File is empty");
        }

        validateFileType(file.getContentType());
        validateFileSize(file.getSize());

        String key = generateDocumentKey(documentType);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        // Add metadata for better organization
        metadata.addUserMetadata("document-type", documentType);
        metadata.addUserMetadata("upload-timestamp", String.valueOf(System.currentTimeMillis()));

        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
        }

        return generateS3Url(key);
    }

    // Method to upload profile image
    public String uploadProfileImageToS3(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new S3UploadException("Profile image file is empty");
        }

        validateFileType(file.getContentType());
        validateFileSize(file.getSize());

        String key = generateProfileImageKey();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        metadata.addUserMetadata("image-type", "profile");
        metadata.addUserMetadata("upload-timestamp", String.valueOf(System.currentTimeMillis()));

        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
        }

        return generateS3Url(key);
    }

    public String uploadAudioToS3(InputStream inputStream, String contentType, long contentLength) throws IOException {
        String key = generateUniqueKeyForAudio();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);

        amazonS3.putObject(new PutObjectRequest(bucketName, key, inputStream, metadata));
        return generateS3Url(key);
    }

    // Validation methods
    private void validateFileType(String contentType) throws S3UploadException {
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new S3UploadException("Invalid file type. Allowed types: " + ALLOWED_IMAGE_TYPES);
        }
    }

    private void validateFileSize(long fileSize) throws S3UploadException {
        if (fileSize > MAX_FILE_SIZE) {
            throw new S3UploadException("File size exceeds maximum limit of " + (MAX_FILE_SIZE / (1024 * 1024)) + "MB");
        }
    }

    // Key generation methods
    private String generateUniqueKey() {
        return "uploads/" + UUID.randomUUID().toString();
    }

    private String generateDocumentKey(String documentType) {
        return "documents/" + documentType.toLowerCase() + "/" + UUID.randomUUID().toString();
    }

    private String generateProfileImageKey() {
        return "profiles/" + UUID.randomUUID().toString();
    }

    private String generateUniqueKeyForAudio() {
        return "audio/" + UUID.randomUUID().toString();
    }

    private String generateS3Url(String key) {
        String url = "https://" + bucketName + ".s3.amazonaws.com/" + key;
        System.out.println("Generated S3 URL: " + url);
        return url;
    }

    // Delete file from S3 (useful for cleanup)
    public void deleteFromS3(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);
            amazonS3.deleteObject(bucketName, key);
        } catch (Exception e) {
            System.err.println("Error deleting file from S3: " + e.getMessage());
        }
    }

    private String extractKeyFromUrl(String fileUrl) {
        return fileUrl.substring(fileUrl.indexOf(".com/") + 5);
    }

    public static class S3UploadException extends IOException {
        public S3UploadException(String message) {
            super(message);
        }

        public S3UploadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}