package com.velorix.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alert_rules")
public class AlertRule {
    @Id
    private String id;
    private String userId;
    private String name;                // e.g., "High error rate"
    private int errorThreshold;         // number of errors in time window
    private int timeWindowMinutes;      // default 10
    private List<String> channels;      // ["email", "telegram"]
    private String email;               // if email channel
    private String telegramChatId;      // if telegram channel
    private boolean enabled;
    private LocalDateTime createdAt;
}