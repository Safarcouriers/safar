package com.saffaricarrers.saffaricarrers.Controller;


import com.saffaricarrers.saffaricarrers.Dtos.LocationSearchResponse;
import com.saffaricarrers.saffaricarrers.Entity.CarrierRoute;
import com.saffaricarrers.saffaricarrers.Repository.CarrierRouteRepository;
import com.saffaricarrers.saffaricarrers.Repository.PackageRepository;
import com.saffaricarrers.saffaricarrers.Services.LocationSearchService;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
@CrossOrigin(origins = "*")
public class LocationSearchController {

    @Autowired
    private LocationSearchService locationSearchService;
    @Autowired
    private CarrierRouteRepository carrierRouteRepository;
    @Autowired
    private PackageRepository packageRepository;

    /**
     * Search for carriers and packages within radius
     * GET /api/location/search?latitude=17.4065&longitude=78.4772&radius=15
     */
    @GetMapping("/search")
    public ResponseEntity<LocationSearchResponse> searchByRadius(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10.0") double radius) {
        try {
            System.out.println("latitude: " + latitude + " longitude: " + longitude + " radius: " + radius);
            LocationSearchResponse response = locationSearchService.searchByRadius(latitude, longitude, radius);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();  // ADD THIS
            System.err.println("Error in search: " + e.getMessage());  // ADD THIS
            return ResponseEntity.badRequest().body(null);
        }
    }
    @GetMapping("/debug/all")
    public ResponseEntity<Map<String, Object>> debugAllData() {
        Map<String, Object> output = new HashMap<>();
        try {
            List<CarrierRoute> carriers = carrierRouteRepository.findAll();
         //   List<Package> packages = packageRepository.findAll();

            output.put("carriers", carriers);
        //    output.put("packages", packages);

            // Print for debug
            System.out.println("=== CARRIERS ===");
            carriers.forEach(c -> System.out.println(c));
            System.out.println("=== PACKAGES ===");
            //packages.forEach(p -> System.out.println(p));

            return ResponseEntity.ok(output);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
//    @GetMapping("/all")
//    public ResponseEntity<LocationSearchResponse> getAllData() {
//        LocationSearchResponse response = locationSearchService.fetchAllCarriersAndPackages();
//        return ResponseEntity.ok(response);
//    }

}