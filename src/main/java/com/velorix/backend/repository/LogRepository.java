package com.velorix.backend.repository;

import com.velorix.backend.model.LogEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends MongoRepository<LogEntry, String> {
    List<LogEntry> findByUserIdAndLevelAndTimestampAfter(String userId, String level, LocalDateTime timestamp);
    long countByUserIdAndLevelAndTimestampAfter(String userId, String level, LocalDateTime timestamp);
}