package com.saffaricarrers.saffaricarrers.Configaration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // ✅ Add Netlify domain here
        config.setAllowedOriginPatterns(List.of(
                "http://127.0.0.1:*",
                "http://localhost:*",
                "https://*.ngrok-free.app",
                "https://yeshwantreddyanugu.github.io",
                "https://ksrmatrimonywarangal.netlify.app" ,
                "https://ksrmatrimony.com",
                "https://preview--matrimony-admin-insights.lovable.app",
                "https://preview--matrimony-admin-insights-40.lovable.app/",
                "https://828c1cda-4006-4f68-80f4-ef9a174831ff.lovableproject.com",
                "https://ksrmatrimony-adminpanel.netlify.app",
                "https://v778wjq4-5500.inc1.devtunnels.ms",
                "https://preview--design-forge-dash.lovable.app/",
                "https://preview--design-opus-admin.lovable.app/",
                "https://41ac0cfe-dbad-4536-99a5-5d24970c53b2.lovableproject.com",
                "https://6d904786-4372-4ea6-9f22-cd84d4ae4028.lovableproject.com",
                "https://54d567ad-9eb4-41b3-8cfb-acabc503870f.lovableproject.com",
                "https://id-preview--1610ccee-d044-4e97-b0b8-5d33266dd356.lovable.app",
                "https://1610ccee-d044-4e97-b0b8-5d33266dd356.lovableproject.com",
                "https://design-project-adminpanel.netlify.app",
                "https://preview--design-curator-99.lovable.app",
                "https://preview--pattern-forge-hub-59.lovable.app",
                "https://preview--pattern-forge-hub-53.lovable.app",
                "https://preview--pattern-forge-hub-53.lovable.app",
                "https://preview--pattern-forge-hub-79.lovable.app",
                "https://preview--pattern-forge-hub-81.lovable.app",
                "https://preview--pattern-forge-hub-62.lovable.app",
                "https://v778wjq4-8081.inc1.devtunnels.ms",
                "https://qawbxya-abhishek240517-8081.exp.direct",
                "https://fzromue-abhishek240517-8081.exp.direct",
                "https://realtysync-connect-03383.vercel.app",
                "https://preview--courier-board.lovable.app",
                "https://preview--bright-admin-dash.lovable.app"
                // 👈 Add this line
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
