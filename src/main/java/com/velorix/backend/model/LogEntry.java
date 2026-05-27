package com.velorix.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "logs")
public class LogEntry {
    @Id
    private String id;
    private String userId;
    private String projectId;      // optional, for future multi-project support
    private String level;          // INFO, WARN, ERROR
    private String message;
    private String source;         // service name or component
    @Indexed(expireAfterSeconds = 604800) // TTL: 7 days in seconds
    private LocalDateTime timestamp;
}