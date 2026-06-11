package com.velorix.backend.controller;

import com.velorix.backend.model.ApiEndpoint;
import com.velorix.backend.repository.ApiEndpointRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/endpoints")

public class ApiEndpointController {

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    @PostMapping
    public ResponseEntity<?> createEndpoint(@RequestBody ApiEndpoint endpoint,
                                            HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        endpoint.setUserId(userId);
        endpoint.setCreatedAt(LocalDateTime.now());
        endpoint.setActive(true);
        endpoint.setCheckIntervalSeconds(300);
        ApiEndpoint saved = apiEndpointRepository.save(endpoint);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<ApiEndpoint>> getUserEndpoints(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(apiEndpointRepository.findByUserId(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEndpoint(@PathVariable String id,
                                            @RequestBody ApiEndpoint updated,
                                            HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        ApiEndpoint existing = apiEndpointRepository.findById(id).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        existing.setName(updated.getName());
        existing.setUrl(updated.getUrl());
        existing.setActive(updated.isActive());
        apiEndpointRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEndpoint(@PathVariable String id,
                                            HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        ApiEndpoint existing = apiEndpointRepository.findById(id).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
        }
        apiEndpointRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}