package com.velorix.backend.controller;

import com.velorix.backend.model.ApiEndpoint;
import com.velorix.backend.repository.ApiEndpointRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/endpoints")
public class ApiEndpointController {

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Extract user ID from JWT token in Authorization header
     */
    private String getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    /**
     * GET /api/endpoints - Get all endpoints for logged-in user
     */
    @GetMapping
    public ResponseEntity<List<ApiEndpoint>> getUserEndpoints(HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);
            List<ApiEndpoint> endpoints = apiEndpointRepository.findByUserId(userId);
            log.info("Fetched {} endpoints for user: {}", endpoints.size(), userId);
            return ResponseEntity.ok(endpoints);
        } catch (Exception e) {
            log.error("Error fetching endpoints: {}", e.getMessage());
            return ResponseEntity.status(401).body(null);
        }
    }

    /**
     * POST /api/endpoints - Create new endpoint
     */
    @PostMapping
    public ResponseEntity<?> createEndpoint(@RequestBody ApiEndpoint endpoint,
                                            HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);
            endpoint.setUserId(userId);
            endpoint.setCreatedAt(LocalDateTime.now());
            endpoint.setActive(true);
            endpoint.setCheckIntervalSeconds(300);

            ApiEndpoint saved = apiEndpointRepository.save(endpoint);
            log.info("Endpoint created: {} for user: {}", endpoint.getName(), userId);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error creating endpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/endpoints/{id} - Update endpoint
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEndpoint(@PathVariable String id,
                                            @RequestBody ApiEndpoint updated,
                                            HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);

            Optional<ApiEndpoint> existingOpt = apiEndpointRepository.findById(id);
            if (existingOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Endpoint not found"));
            }

            ApiEndpoint existing = existingOpt.get();
            if (!existing.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }

            existing.setName(updated.getName());
            existing.setUrl(updated.getUrl());
            existing.setActive(updated.isActive());

            ApiEndpoint saved = apiEndpointRepository.save(existing);
            log.info("Endpoint updated: {} by user: {}", id, userId);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error updating endpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/endpoints/{id} - Delete endpoint
     * Security: Verify endpoint belongs to authenticated user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEndpoint(@PathVariable String id,
                                            HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);

            // Find endpoint
            Optional<ApiEndpoint> existingOpt = apiEndpointRepository.findById(id);
            if (existingOpt.isEmpty()) {
                log.warn("Delete attempt: Endpoint {} not found", id);
                return ResponseEntity.status(404).body(Map.of("error", "Endpoint not found"));
            }

            ApiEndpoint existing = existingOpt.get();

            // Verify ownership
            if (!existing.getUserId().equals(userId)) {
                log.warn("Delete attempt: User {} tried to delete endpoint {} owned by {}",
                        userId, id, existing.getUserId());
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }

            // Delete
            apiEndpointRepository.deleteById(id);
            log.info("Endpoint deleted: {} ({}) by user: {}", id, existing.getName(), userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Endpoint deleted successfully",
                    "deletedId", id,
                    "deletedName", existing.getName()
            ));
        } catch (Exception e) {
            log.error("Error deleting endpoint: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}