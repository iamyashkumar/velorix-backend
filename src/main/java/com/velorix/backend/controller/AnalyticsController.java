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
                return ResponseEntity.ok(Map.of(
                        "totalEndpoints", 0,
                        "upEndpoints", 0,
                        "downEndpoints", 0,
                        "uptimePercentage", 0.0,
                        "averageResponseTime", 0,
                        "totalRequests", 0,
                        "errorRate", 0.0
                ));
            }

            // Calculate uptime
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            int upCount = 0;
            int downCount = 0;
            long totalResponseTime = 0;
            int totalChecks = 0;
            int failedChecks = 0;

            for (ApiEndpoint endpoint : endpoints) {
                List<HealthLog> logs = healthLogRepository.findByEndpointIdAndCheckedAtAfter(
                        endpoint.getId(), sevenDaysAgo
                );

                for (HealthLog log : logs) {
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

            double uptimePercentage = totalChecks > 0 ? (upCount * 100.0) / totalChecks : 0;
            int averageResponseTime = totalChecks > 0 ? (int) (totalResponseTime / totalChecks) : 0;
            double errorRate = totalChecks > 0 ? (failedChecks * 100.0) / totalChecks : 0;

            return ResponseEntity.ok(Map.of(
                    "totalEndpoints", endpoints.size(),
                    "upEndpoints", (int) endpoints.stream().filter(ApiEndpoint::isActive).count(),
                    "downEndpoints", (int) endpoints.stream().filter(e -> !e.isActive()).count(),
                    "uptimePercentage", String.format("%.2f", uptimePercentage),
                    "averageResponseTime", averageResponseTime,
                    "totalRequests", totalChecks,
                    "errorRate", String.format("%.2f", errorRate),
                    "slaStatus", Double.parseDouble(String.format("%.2f", uptimePercentage)) >= 99.0 ? "✅ Met" : "⚠️ At Risk"
            ));
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
            List<HealthLog> logs = healthLogRepository.findByEndpointIdAndCheckedAtAfter(id, startDate);

            if (logs.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "endpointName", endpoint.getName(),
                        "uptimePercentage", 0.0,
                        "averageResponseTime", 0,
                        "maxResponseTime", 0,
                        "minResponseTime", 0,
                        "totalChecks", 0,
                        "failures", 0
                ));
            }

            int upCount = (int) logs.stream().filter(HealthLog::isUp).count();
            int downCount = logs.size() - upCount;
            long totalResponseTime = logs.stream().mapToLong(HealthLog::getResponseTimeMs).sum();
            long maxResponseTime = logs.stream().mapToLong(HealthLog::getResponseTimeMs).max().orElse(0);
            long minResponseTime = logs.stream().mapToLong(HealthLog::getResponseTimeMs).min().orElse(0);
            double uptimePercentage = (upCount * 100.0) / logs.size();
            int averageResponseTime = (int) (totalResponseTime / logs.size());

            return ResponseEntity.ok(Map.of(
                    "endpointName", endpoint.getName(),
                    "endpointUrl", endpoint.getUrl(),
                    "daysAnalyzed", days,
                    "uptimePercentage", String.format("%.2f", uptimePercentage),
                    "averageResponseTime", averageResponseTime,
                    "maxResponseTime", maxResponseTime,
                    "minResponseTime", minResponseTime,
                    "totalChecks", logs.size(),
                    "successfulChecks", upCount,
                    "failures", downCount,
                    "failureRate", String.format("%.2f", (downCount * 100.0) / logs.size())
            ));
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
            List<HealthLog> logs = healthLogRepository.findByEndpointIdAndCheckedAtAfter(id, startDate);

            // Group by day
            Map<String, Map<String, Object>> dailyStats = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getCheckedAt().toLocalDate().toString(),
                            Collectors.collectingAndThen(
                                    Collectors.toList(),
                                    dayLogs -> {
                                        long upCount = dayLogs.stream().filter(HealthLog::isUp).count();
                                        double uptime = (upCount * 100.0) / dayLogs.size();
                                        long avgResponseTime = (long) dayLogs.stream()
                                                .mapToLong(HealthLog::getResponseTimeMs)
                                                .average()
                                                .orElse(0);

                                        return Map.of(
                                                "date", log.getCheckedAt().toLocalDate().toString(),
                                                "uptime", String.format("%.1f", uptime),
                                                "avgResponseTime", avgResponseTime,
                                                "checks", dayLogs.size()
                                        );
                                    }
                            )
                    ));

            List<Map<String, Object>> trendData = dailyStats.values().stream()
                    .sorted((a, b) -> a.get("date").toString().compareTo(b.get("date").toString()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "endpointName", endpoint.getName(),
                    "trendData", trendData
            ));
        } catch (Exception e) {
            log.error("Error fetching endpoint trend: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}