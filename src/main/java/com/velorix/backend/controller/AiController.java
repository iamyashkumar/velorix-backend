package com.velorix.backend.controller;

import com.velorix.backend.model.AiSuggestion;
import com.velorix.backend.model.User;
import com.velorix.backend.repository.AiSuggestionRepository;
import com.velorix.backend.repository.UserRepository;
import com.velorix.backend.security.JwtUtil;
import com.velorix.backend.service.AiDebugService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiDebugService aiDebugService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;

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

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeErrors(@RequestBody(required = false) Map<String, String> payload,
                                           HttpServletRequest request) {
        try {
            String userId = getUserIdFromRequest(request);
            String endpointId = (payload != null) ? payload.get("endpointId") : null;

            // Rate limiting: max 10 calls per day per user
            User user = userRepository.findById(userId).orElseThrow();
            LocalDateTime todayMidnight = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            if (user.getLastAiCallReset() == null || user.getLastAiCallReset().isBefore(todayMidnight)) {
                user.setDailyAiCalls(0);
                user.setLastAiCallReset(LocalDateTime.now());
            }
            if (user.getDailyAiCalls() >= 10) {
                return ResponseEntity.status(429).body(Map.of("error", "Daily AI limit reached (10 calls). Try tomorrow."));
            }

            AiSuggestion suggestion = aiDebugService.analyzeErrors(userId, endpointId);

            user.setDailyAiCalls(user.getDailyAiCalls() + 1);
            userRepository.save(user);

            return ResponseEntity.ok(suggestion);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<AiSuggestion>> getHistory(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        List<AiSuggestion> history = aiSuggestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(history);
    }
}