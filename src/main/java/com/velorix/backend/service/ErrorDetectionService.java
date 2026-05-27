package com.velorix.backend.service;

import com.velorix.backend.model.AlertRule;
import com.velorix.backend.repository.AlertRuleRepository;
import com.velorix.backend.repository.LogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@EnableScheduling
public class ErrorDetectionService {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertService alertService;

    // Runs every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void detectErrorSpikes() {
        List<AlertRule> activeRules = alertRuleRepository.findByEnabledTrue();

        for (AlertRule rule : activeRules) {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(rule.getTimeWindowMinutes());
            long errorCount = logRepository.countByUserIdAndLevelAndTimestampAfter(
                    rule.getUserId(), "ERROR", cutoff
            );

            if (errorCount >= rule.getErrorThreshold()) {
                String message = String.format(
                        "⚠️ Alert: %d errors in last %d minutes (threshold: %d)",
                        errorCount, rule.getTimeWindowMinutes(), rule.getErrorThreshold()
                );
                alertService.sendAlert(rule, message);
            }
        }
    }
}