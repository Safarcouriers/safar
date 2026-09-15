package com.saffaricarrers.saffaricarrers.N1.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@Slf4j
public class N1FirebaseAuthService {

    private static final String N1_FIREBASE_APP_NAME =
            "n1NotificationListenerApp";

    private static final String SERVICE_ACCOUNT_FILE =
            "notificationlistenerapp-firebase-adminsdk-d7hvu-4184a9900d.json";

    private FirebaseAuth getN1FirebaseAuth() {

        try {

            FirebaseApp n1App;

            // Check whether our separate N1 Firebase app already exists
            try {

                n1App = FirebaseApp.getInstance(
                        N1_FIREBASE_APP_NAME
                );

            } catch (IllegalStateException e) {

                log.info(
                        "Initializing separate N1 Firebase app using {}",
                        SERVICE_ACCOUNT_FILE
                );

                ClassPathResource resource =
                        new ClassPathResource(
                                SERVICE_ACCOUNT_FILE
                        );

                if (!resource.exists()) {

                    throw new IllegalStateException(
                            "N1 Firebase service account file not found: "
                                    + SERVICE_ACCOUNT_FILE
                    );
                }

                try (InputStream serviceAccount =
                             resource.getInputStream()) {

                    FirebaseOptions options =
                            FirebaseOptions.builder()
                                    .setCredentials(
                                            GoogleCredentials.fromStream(
                                                    serviceAccount
                                            )
                                    )
                                    .build();

                    n1App =
                            FirebaseApp.initializeApp(
                                    options,
                                    N1_FIREBASE_APP_NAME
                            );
                }
            }

            return FirebaseAuth.getInstance(n1App);

        } catch (Exception e) {

            log.error(
                    "N1 Firebase initialization failed: {}",
                    e.getMessage(),
                    e
            );

            throw new IllegalStateException(
                    "N1 Firebase initialization failed",
                    e
            );
        }
    }

    public String verify(String authorizationHeader) {

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            log.warn(
                    "N1 Firebase auth failed: Authorization header missing"
            );

            throw new SecurityException(
                    "Missing Firebase ID token"
            );
        }

        String token =
                authorizationHeader
                        .substring(7)
                        .trim();

        if (token.isBlank()) {

            log.warn(
                    "N1 Firebase auth failed: token is empty"
            );

            throw new SecurityException(
                    "Missing Firebase ID token"
            );
        }

        try {

            FirebaseAuth firebaseAuth =
                    getN1FirebaseAuth();

            FirebaseToken decoded =
                    firebaseAuth.verifyIdToken(token);

            String uid =
                    decoded.getUid();

            log.info(
                    "N1 Firebase token verified successfully user={}",
                    uid
            );

            return uid;

        } catch (Exception e) {

            log.error(
                    "N1 Firebase ID token verification failed. type={} message={}",
                    e.getClass().getName(),
                    e.getMessage()
            );

            throw new SecurityException(
                    "Invalid Firebase ID token"
            );
        }
    }
}