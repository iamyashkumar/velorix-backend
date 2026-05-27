package com.velorix.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "health_logs")
public class HealthLog {
    @Id
    private String id;

    private String endpointId;

    private String userId;

    private int statusCode;

    private long responseTimeMs;

    private boolean isUp;

    private LocalDateTime checkedAt;
}
