package com.velorix.backend.controller;

import com.velorix.backend.model.LogEntry;
import com.velorix.backend.repository.LogRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*")
public class LogController {

    @Autowired
    private LogRepository logRepository;

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

    @PostMapping
    public ResponseEntity<?> ingestLog(@RequestBody LogEntry logEntry, HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        logEntry.setUserId(userId);
        logEntry.setTimestamp(LocalDateTime.now());
        logRepository.save(logEntry);
        return ResponseEntity.ok(Map.of("message", "Log ingested"));
    }

    @GetMapping
    public ResponseEntity<?> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        String userId = getUserIdFromRequest(request);

        // Build query criteria
        Criteria criteria = Criteria.where("userId").is(userId);
        if (level != null && !level.isEmpty()) {
            criteria = criteria.and("level").is(level);
        }
        if (keyword != null && !keyword.isEmpty()) {
            criteria = criteria.and("message").regex(keyword, "i"); // case‑insensitive search
        }

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, LogEntry.class);
        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        List<LogEntry> logs = mongoTemplate.find(query, LogEntry.class);

        int totalPages = (int) Math.ceil((double) total / size);
        return ResponseEntity.ok(Map.of(
                "content", logs,
                "totalPages", totalPages,
                "totalElements", total,
                "page", page,
                "size", size
        ));
    }
}