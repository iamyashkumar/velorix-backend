package com.velorix.backend.controller;

import com.velorix.backend.model.ApiEndpoint;
import com.velorix.backend.model.HealthLog;
import com.velorix.backend.repository.ApiEndpointRepository;
import com.velorix.backend.repository.HealthLogRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private HealthLogRepository healthLogRepository;

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

    /**
     * GET /api/analytics/summary - Get overall analytics summary
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getAnalyticsSummary(HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);
            List<ApiEndpoint> endpoints = apiEndpointRepository.findByUserId(userId);

            if (endpoints.isEmpty()) {
                Map<String, Object> emptyResponse = new LinkedHashMap<>();
                emptyResponse.put("totalEndpoints", 0);
                emptyResponse.put("upEndpoints", 0);
                emptyResponse.put("downEndpoints", 0);
                emptyResponse.put("uptimePercentage", "0.00");
                emptyResponse.put("averageResponseTime", 0);
                emptyResponse.put("totalRequests", 0);
                emptyResponse.put("errorRate", "0.00");
                return ResponseEntity.ok(emptyResponse);
            }

            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            int upCount = 0;
            int downCount = 0;
            long totalResponseTime = 0;
            int totalChecks = 0;
            int failedChecks = 0;

            for (ApiEndpoint endpoint : endpoints) {
                List<HealthLog> logs = healthLogRepository.findByEndpointId(endpoint.getId());

                for (HealthLog log : logs) {
                    if (log.getCheckedAt() != null && log.getCheckedAt().isAfter(sevenDaysAgo)) {
                        totalChecks++;
                        if (log.isUp()) {
                            upCount++;
                        } else {
                            downCount++;
                            failedChecks++;
                        }
                        totalResponseTime += log.getResponseTimeMs();
                    }
                }
            }

            double uptimePercentage = totalChecks > 0 ? (upCount * 100.0) / totalChecks : 0;
            int averageResponseTime = totalChecks > 0 ? (int) (totalResponseTime / totalChecks) : 0;
            double errorRate = totalChecks > 0 ? (failedChecks * 100.0) / totalChecks : 0;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalEndpoints", endpoints.size());
            response.put("upEndpoints", (int) endpoints.stream().filter(ApiEndpoint::isActive).count());
            response.put("downEndpoints", (int) endpoints.stream().filter(e -> !e.isActive()).count());
            response.put("uptimePercentage", String.format("%.2f", uptimePercentage));
            response.put("averageResponseTime", averageResponseTime);
            response.put("totalRequests", totalChecks);
            response.put("errorRate", String.format("%.2f", errorRate));
            response.put("slaStatus", Double.parseDouble(String.format("%.2f", uptimePercentage)) >= 99.0 ? "✅ Met" : "⚠️ At Risk");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching analytics summary: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/endpoint/{id} - Get endpoint-specific analytics
     */
    @GetMapping("/endpoint/{id}")
    public ResponseEntity<?> getEndpointAnalytics(
            @PathVariable String id,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request
    ) {
        try {
            String userId = getUserIdFromRequest(request);
            ApiEndpoint endpoint = apiEndpointRepository.findById(id).orElse(null);

            if (endpoint == null || !endpoint.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<HealthLog> logs = healthLogRepository.findByEndpointId(id);

            // Filter by date
            logs = logs.stream()
                    .filter(log -> log.getCheckedAt() != null && log.getCheckedAt().isAfter(startDate))
                    .collect(Collectors.toList());

            if (logs.isEmpty()) {
                Map<String, Object> emptyResponse = new LinkedHashMap<>();
                emptyResponse.put("endpointName", endpoint.getName());
                emptyResponse.put("uptimePercentage", "0.00");
                emptyResponse.put("averageResponseTime", 0);
                emptyResponse.put("maxResponseTime", 0);
                emptyResponse.put("minResponseTime", 0);
                emptyResponse.put("totalChecks", 0);
                emptyResponse.put("failures", 0);
                return ResponseEntity.ok(emptyResponse);
            }

            int upCount = (int) logs.stream().filter(HealthLog::isUp).count();
            int downCount = logs.size() - upCount;
            long totalResponseTime = logs.stream().mapToLong(HealthLog::getResponseTimeMs).sum();
            long maxResponseTime = logs.stream().mapToLong(HealthLog::getResponseTimeMs).max().orElse(0);
            long minResponseTime = logs.stream().mapToLong(HealthLog::getResponseTimeMs).min().orElse(0);
            double uptimePercentage = (upCount * 100.0) / logs.size();
            int averageResponseTime = (int) (totalResponseTime / logs.size());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("endpointName", endpoint.getName());
            response.put("endpointUrl", endpoint.getUrl());
            response.put("daysAnalyzed", days);
            response.put("uptimePercentage", String.format("%.2f", uptimePercentage));
            response.put("averageResponseTime", averageResponseTime);
            response.put("maxResponseTime", maxResponseTime);
            response.put("minResponseTime", minResponseTime);
            response.put("totalChecks", logs.size());
            response.put("successfulChecks", upCount);
            response.put("failures", downCount);
            response.put("failureRate", String.format("%.2f", (downCount * 100.0) / logs.size()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching endpoint analytics: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/analytics/trend/{id} - Get trend data for chart
     */
    @GetMapping("/trend/{id}")
    public ResponseEntity<?> getEndpointTrend(
            @PathVariable String id,
            @RequestParam(defaultValue = "7") int days,
            HttpServletRequest request
    ) {
        try {
            String userId = getUserIdFromRequest(request);
            ApiEndpoint endpoint = apiEndpointRepository.findById(id).orElse(null);

            if (endpoint == null || !endpoint.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized"));
            }

            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<HealthLog> logs = healthLogRepository.findByEndpointId(id);

            // Filter by date
            logs = logs.stream()
                    .filter(log -> log.getCheckedAt() != null && log.getCheckedAt().isAfter(startDate))
                    .collect(Collectors.toList());

            // Group by day
            Map<String, List<HealthLog>> dailyLogs = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getCheckedAt().toLocalDate().toString()
                    ));

            List<Map<String, Object>> trendData = new ArrayList<>();

            for (String date : dailyLogs.keySet()) {
                List<HealthLog> dayLogs = dailyLogs.get(date);
                long upCount = dayLogs.stream().filter(HealthLog::isUp).count();
                double uptime = dayLogs.size() > 0 ? (upCount * 100.0) / dayLogs.size() : 0;
                long avgResponseTime = dayLogs.stream()
                        .mapToLong(HealthLog::getResponseTimeMs)
                        .average()
                        .orElse(0);

                Map<String, Object> dayData = new LinkedHashMap<>();
                dayData.put("date", date);
                dayData.put("uptime", String.format("%.1f", uptime));
                dayData.put("avgResponseTime", avgResponseTime);
                dayData.put("checks", dayLogs.size());

                trendData.add(dayData);
            }

            // Sort by date
            trendData.sort((a, b) -> a.get("date").toString().compareTo(b.get("date").toString()));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("endpointName", endpoint.getName());
            response.put("trendData", trendData);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching endpoint trend: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}