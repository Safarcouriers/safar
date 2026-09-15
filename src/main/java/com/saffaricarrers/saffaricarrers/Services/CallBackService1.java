package com.saffaricarrers.saffaricarrers.Services;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class CallBackService1 {
    @Autowired
    private AmazonS3 amazonS3;

    @Value("${aws.s3.bucket}")
    public String BucketName;

    public String uploadToS3(InputStream inputStream, String contentType, long contentLength) throws IOException {
        String key = generateUniqueKey();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);
        amazonS3.putObject(new PutObjectRequest(BucketName, key, inputStream, metadata));
        return generateS3Url(key);
    }

    private String generateUniqueKey() {
        return UUID.randomUUID().toString();
    }

    private String generateS3Url(String key) {
        System.out.println("https://" + BucketName + ".s3.amazonaws.com/" + key);
        return "https://" + BucketName + ".s3.amazonaws.com/" + key;
    }

    public String uploadAudioToS3(InputStream inputStream, String contentType, long contentLength) throws IOException {
        String key = generateUniqueKeyForAudio();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);
        amazonS3.putObject(new PutObjectRequest(BucketName, key, inputStream, metadata));
        return generateS3Url(key);
    }

    private String generateUniqueKeyForAudio() {
        return "audio/" + UUID.randomUUID().toString();
    }

    // Improved method to upload video as MP4
    public String uploadVideoToS3(InputStream inputStream, long contentLength) throws IOException {
        // Generate unique key with .mp4 extension
        String key = generateUniqueKeyForVideo();

        // Set metadata specifically for MP4 video
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("video/mp4"); // Force MP4 content type
        metadata.setContentLength(contentLength);

        // Optional: Add additional metadata for better handling
        metadata.setCacheControl("max-age=31536000"); // Cache for 1 year
        metadata.setHeader("Content-Disposition", "inline"); // Allow inline viewing

        // Upload to S3
        amazonS3.putObject(new PutObjectRequest(BucketName, key, inputStream, metadata));

        // Return the MP4 URL
        return generateS3Url(key);
    }

    // Alternative method if you want to keep contentType parameter for flexibility
    public String uploadVideoToS3(InputStream inputStream, String contentType, long contentLength) throws IOException {
        String key = generateUniqueKeyForVideo();

        ObjectMetadata metadata = new ObjectMetadata();
        // Always force MP4 content type regardless of input contentType
        metadata.setContentType("video/mp4");
        metadata.setContentLength(contentLength);
        metadata.setCacheControl("max-age=31536000");
        metadata.setHeader("Content-Disposition", "inline");

        amazonS3.putObject(new PutObjectRequest(BucketName, key, inputStream, metadata));
        return generateS3Url(key);
    }

    private String generateUniqueKeyForVideo() {
        // Generate key with .mp4 extension to ensure proper file handling
        return "video/" + UUID.randomUUID().toString() + ".mp4";
    }
}