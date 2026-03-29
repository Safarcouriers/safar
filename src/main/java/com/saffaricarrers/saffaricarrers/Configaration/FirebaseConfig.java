package com.saffaricarrers.saffaricarrers.Configaration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.service-account-key:firebase-service-account-key.json}")
    private String serviceAccountKeyPath;

    @PostConstruct
    public void initialize() {
        try {
            initializeFirebase();
        } catch (IOException e) {
            log.error("Failed to initialize Firebase", e);
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    private void initializeFirebase() throws IOException {
        // Check if Firebase is already initialized
        if (FirebaseApp.getApps().isEmpty()) {

            InputStream serviceAccount = null;

            try {
                // Try to load from classpath first
                ClassPathResource resource = new ClassPathResource(serviceAccountKeyPath);
                if (resource.exists()) {
                    serviceAccount = resource.getInputStream();
                    log.info("Loading Firebase service account from classpath: {}", serviceAccountKeyPath);
                } else {
                    // Try to load from file system
                    serviceAccount = new FileInputStream(serviceAccountKeyPath);
                    log.info("Loading Firebase service account from file system: {}", serviceAccountKeyPath);
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully for FCM");

            } catch (Exception e) {
                log.error("Error initializing Firebase with service account", e);
                // Fallback to default credentials (useful for Google Cloud deployment)
                try {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.getApplicationDefault())
                            .build();

                    FirebaseApp.initializeApp(options);
                    log.info("Firebase initialized with default credentials");
                } catch (Exception defaultCredsException) {
                    log.error("Failed to initialize Firebase with default credentials", defaultCredsException);
                    throw new RuntimeException("Firebase initialization failed", defaultCredsException);
                }
            } finally {
                if (serviceAccount != null) {
                    try {
                        serviceAccount.close();
                    } catch (IOException e) {
                        log.warn("Error closing service account input stream", e);
                    }
                }
            }
        } else {
            log.info("Firebase already initialized");
        }
    }

    @Bean
    public FirebaseApp firebaseApp() {
        return FirebaseApp.getInstance();
    }
}
