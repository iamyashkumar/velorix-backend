package com.velorix.backend.repository;

import com.velorix.backend.model.AiSuggestion;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AiSuggestionRepository extends MongoRepository<AiSuggestion, String> {
    List<AiSuggestion> findByUserIdOrderByCreatedAtDesc(String userId);
    List<AiSuggestion> findByUserIdAndEndpointId(String userId, String endpointId);
}