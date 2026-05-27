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
@Document(collection = "api_endpoints")
public class ApiEndpoint {
    @Id
    private String id;

    private String userId;

    private String name;

    private String url;

    private int checkIntervalSeconds;

    private boolean isActive;

    private LocalDateTime createdAt;
}