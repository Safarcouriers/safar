package com.saffaricarrers.saffaricarrers.Configaration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
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
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized");
            return;
        }

        InputStream serviceAccount = null;
        try {
            ClassPathResource resource = new ClassPathResource(serviceAccountKeyPath);
            if (resource.exists()) {
                serviceAccount = resource.getInputStream();
                log.info("Firebase: loading from classpath: {}", serviceAccountKeyPath);
            } else {
                serviceAccount = new FileInputStream(serviceAccountKeyPath);
                log.info("Firebase: loading from filesystem: {}", serviceAccountKeyPath);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase initialized (FCM + Firestore ready)");

        } catch (Exception e) {
            log.warn("Service account load failed, trying default credentials: {}", e.getMessage());
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized with default credentials");
            } catch (Exception ex) {
                throw new RuntimeException("Firebase initialization failed", ex);
            }
        } finally {
            if (serviceAccount != null) {
                try { serviceAccount.close(); }
                catch (IOException e) { log.warn("Error closing service account stream", e); }
            }
        }
    }

    // Used by FCM push notifications, Auth, etc.
    @Bean
    public FirebaseApp firebaseApp() {
        return FirebaseApp.getInstance();
    }

    // Used by Firestore queries (your monetization/subscription logic)
    @Bean
    public Firestore firestore() {
        return FirestoreClient.getFirestore();
    }
}