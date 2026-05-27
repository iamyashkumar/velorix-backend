package com.velorix.backend.controller;

import com.velorix.backend.model.AlertRule;
import com.velorix.backend.repository.AlertRuleRepository;
import com.velorix.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = "*")
public class AlertRuleController {

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody AlertRule rule, HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        rule.setUserId(userId);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setEnabled(true);
        AlertRule saved = alertRuleRepository.save(rule);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<AlertRule>> getUserRules(HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(alertRuleRepository.findByUserId(userId));
    }
}