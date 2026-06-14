package com.velorix.backend.controller;

import com.velorix.backend.model.ApiEndpoint;
import com.velorix.backend.repository.ApiEndpointRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    @GetMapping
    public ResponseEntity<List<ApiEndpoint>> getAllEndpoints(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        List<ApiEndpoint> endpoints = apiEndpointRepository.findByUserId(userId);
        return ResponseEntity.ok(endpoints);
    }

    @PostMapping
    public ResponseEntity<?> createEndpoint(@RequestBody ApiEndpoint endpoint, HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);
            endpoint.setUserId(userId);
            endpoint.setActive(true);
            ApiEndpoint saved = apiEndpointRepository.save(endpoint);
            log.info("Endpoint created: {} for user: {}", endpoint.getName(), userId);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error creating endpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEndpoint(@PathVariable String id, HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);

            // Verify endpoint belongs to user
            Optional<ApiEndpoint> endpoint = apiEndpointRepository.findById(id);
            if (endpoint.isEmpty() || !endpoint.get().getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            // Delete endpoint
            apiEndpointRepository.deleteById(id);
            log.info("Endpoint deleted: {} by user: {}", id, userId);
            return ResponseEntity.ok(Map.of("message", "Endpoint deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting endpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}