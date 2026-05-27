package com.velorix.backend.service;

import com.velorix.backend.model.LogEntry;
import com.velorix.backend.model.User;
import com.velorix.backend.repository.LogRepository;
import com.velorix.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final LogRepository logRepository;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // Only seed if logs collection is empty
        if (logRepository.count() > 0) return;

        userRepository.findByEmail("test@railway.com").ifPresent(user -> {
            seedLogs(user.getId());
            System.out.println("✅ Velorix: Seeded demo logs for user " + user.getEmail());
        });
    }

    private void seedLogs(String userId) {
        List<LogEntry> logs = new ArrayList<>();
        Random rnd = new Random();

        String[] sources = {"auth-service", "payment-service", "api-gateway", "user-service", "notification-service"};

        String[] infoMessages = {
                "User login successful",
                "API request processed in 45ms",
                "Cache hit for key user:1234",
                "Health check passed",
                "Database connection pool: 5/20 active",
                "Scheduled job completed successfully",
                "Token refreshed for user session",
                "Request rate: 120 req/min — normal",
                "New user registered: id=5891",
                "Email sent to user@example.com"
        };

        String[] warnMessages = {
                "Response time high: 890ms on /api/users",
                "Memory usage at 78% — monitor closely",
                "Retry attempt 2/3 for payment gateway",
                "Slow query detected: 1200ms on user_logs collection",
                "Rate limit approaching: 85/100 req/min",
                "JWT token expiring in 5 minutes",
                "Cache miss rate high: 42%",
                "Connection pool nearing limit: 18/20"
        };

        String[] errorMessages = {
                "NullPointerException in PaymentService.processPayment() at line 87",
                "MongoDB connection timeout after 30000ms",
                "HTTP 500 on POST /api/payments — upstream service unreachable",
                "Failed to send email: SMTP authentication error",
                "JWT signature verification failed — possible token tampering",
                "OutOfMemoryError in heap space — GC overhead exceeded",
                "Database write failed: duplicate key on users.email index",
                "External API call failed: connection refused at https://api.stripe.com"
        };

        LocalDateTime now = LocalDateTime.now();

        // Generate 7 days of logs
        for (int day = 6; day >= 0; day--) {
            int logsPerDay = 15 + rnd.nextInt(20);
            for (int i = 0; i < logsPerDay; i++) {
                LocalDateTime ts = now.minusDays(day)
                        .minusHours(rnd.nextInt(20))
                        .minusMinutes(rnd.nextInt(60));

                // Distribution: 60% INFO, 25% WARN, 15% ERROR
                int roll = rnd.nextInt(100);
                String level;
                String message;

                if (roll < 60) {
                    level = "INFO";
                    message = infoMessages[rnd.nextInt(infoMessages.length)];
                } else if (roll < 85) {
                    level = "WARN";
                    message = warnMessages[rnd.nextInt(warnMessages.length)];
                } else {
                    level = "ERROR";
                    message = errorMessages[rnd.nextInt(errorMessages.length)];
                }

                logs.add(LogEntry.builder()
                        .userId(userId)
                        .level(level)
                        .message(message)
                        .source(sources[rnd.nextInt(sources.length)])
                        .timestamp(ts)
                        .build());
            }
        }

        logRepository.saveAll(logs);
    }
}
