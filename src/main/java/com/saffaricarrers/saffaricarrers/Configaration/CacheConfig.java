package com.saffaricarrers.saffaricarrers.Configaration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                // User service caches
                "users",                    // Single user cache
                "profiles",                 // Profile completion cache
                "verification",             // Verification status cache
                "userPages",                // Paginated user list cache
                "userPagesWithProfile",     // Paginated users with profiles

                // CarrierRoute service caches
                "carrierRoutes",            // Routes for a specific carrier
                "nearbyRoutes",             // Nearby route searches
                "geospatialSearch",         // Unified geospatial search results
                "routeById"  ,

                "senderPackages",
                "packageById",
                "geospatialPackages",
                "packageStats"// Single route by ID,

        );

        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)                              // Initial cache size
                .maximumSize(10_000)                               // Max 10,000 entries (~40-80 MB)
                .expireAfterWrite(Duration.ofMinutes(10))          // Expire after 10 minutes
                .expireAfterAccess(Duration.ofMinutes(5))          // Expire if not accessed for 5 minutes
                .recordStats();                                    // Enable statistics for monitoring
    }
}