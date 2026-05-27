package com.velorix.backend.repository;

import com.velorix.backend.model.AlertRule;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface AlertRuleRepository extends MongoRepository<AlertRule, String> {
    List<AlertRule> findByUserId(String userId);
    List<AlertRule> findByEnabledTrue();
}