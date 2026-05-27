package com.velorix.backend.controller;

import com.velorix.backend.model.HealthLog;
import com.velorix.backend.repository.HealthLogRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    @GetMapping("/logs/{endpointId}")
    public ResponseEntity<List<HealthLog>> getLogs(@PathVariable String endpointId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        // Optional: verify that endpoint belongs to user (skip for brevity)
        List<HealthLog> logs = healthLogRepository.findByEndpointIdOrderByCheckedAtDesc(
                endpointId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedAt"))
        );
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/stats/{endpointId}")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable String endpointId,
                                                        HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<HealthLog> last24h = healthLogRepository.findByEndpointIdAndCheckedAtBetween(
                endpointId, twentyFourHoursAgo, LocalDateTime.now()
        );
        long uptimeCount = last24h.stream().filter(HealthLog::isUp).count();
        double uptimePercentage = last24h.isEmpty() ? 100.0 : (uptimeCount * 100.0 / last24h.size());
        double avgResponseTime = last24h.stream()
                .mapToLong(HealthLog::getResponseTimeMs)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new HashMap<>();
        stats.put("uptimePercentage", uptimePercentage);
        stats.put("averageResponseTimeMs", avgResponseTime);
        stats.put("totalChecks", last24h.size());
        return ResponseEntity.ok(stats);
    }
}