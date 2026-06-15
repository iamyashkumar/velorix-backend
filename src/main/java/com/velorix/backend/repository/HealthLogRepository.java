package com.velorix.backend.repository;

import com.velorix.backend.model.HealthLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface HealthLogRepository extends MongoRepository<HealthLog, String> {
    List<HealthLog> findByEndpointIdOrderByCheckedAtDesc(String endpointId, Pageable pageable);
    List<HealthLog> findByEndpointIdAndCheckedAtBetween(String endpointId, LocalDateTime start, LocalDateTime end);

    List<HealthLog> findByEndpointId(String id);
}
