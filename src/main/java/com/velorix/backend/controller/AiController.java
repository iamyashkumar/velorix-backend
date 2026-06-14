package com.velorix.backend.controller;

import com.velorix.backend.model.AiSuggestion;
import com.velorix.backend.model.User;
import com.velorix.backend.repository.AiSuggestionRepository;
import com.velorix.backend.repository.UserRepository;
import com.velorix.backend.security.JwtUtil;
import com.velorix.backend.service.AiDebugService;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
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

    @Autowired
    private MongoTemplate mongoTemplate;

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

            // Reset counter if new day
            if (user.getLastAiCallReset() == null || user.getLastAiCallReset().isBefore(todayMidnight)) {
                user.setDailyAiCalls(0);
                user.setLastAiCallReset(LocalDateTime.now());
                userRepository.save(user);
            }

            // Check limit BEFORE atomic increment
            if (user.getDailyAiCalls() >= 10) {
                log.warn("User {} reached daily AI call limit", userId);
                return ResponseEntity.status(429).body(Map.of("error", "Daily AI limit reached (10 calls). Try tomorrow."));
            }

            // Call AI service
            AiSuggestion suggestion = aiDebugService.analyzeErrors(userId, endpointId);

            // Atomic increment - race condition safe
            incrementAiCallCount(userId);

            log.info("User {} AI call successful, total calls today: {}", userId, user.getDailyAiCalls() + 1);
            return ResponseEntity.ok(suggestion);

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("No ERROR logs found")) {
                return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
            }
            log.error("AI analyze error: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Internal server error in AI analyze: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Atomic increment using MongoDB findAndModify
     * This prevents race conditions when multiple requests hit the limit check simultaneously
     */
    private void incrementAiCallCount(String userId) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update().inc("dailyAiCalls", 1);
        mongoTemplate.findAndModify(query, update, User.class);
    }

    @GetMapping("/history")
    public ResponseEntity<List<AiSuggestion>> getHistory(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        List<AiSuggestion> history = aiSuggestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(history);
    }
}