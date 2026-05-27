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
@Document(collection = "ai_suggestions")
public class AiSuggestion {
    @Id
    private String id;
    private String userId;
    private String endpointId;          // optional
    private String possibleCause;
    private String recommendedFix;
    private String severity;            // HIGH, MEDIUM, LOW
    private String originalErrors;      // truncated logs (max 500 chars)
    private LocalDateTime createdAt;
}