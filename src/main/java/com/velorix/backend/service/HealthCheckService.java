package com.velorix.backend.service;

import com.velorix.backend.model.ApiEndpoint;
import com.velorix.backend.model.HealthLog;
import com.velorix.backend.repository.ApiEndpointRepository;
import com.velorix.backend.repository.HealthLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class HealthCheckService {

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private HealthLogRepository healthLogRepository;

    private final RestTemplate restTemplate = createRestTemplate();

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5 seconds to establish connection
        factory.setReadTimeout(10000);    // 10 seconds to read response
        return new RestTemplate(factory);
    }

    @Scheduled(fixedRate = 300000)
    public void checkAllEndpoints() {
        List<ApiEndpoint> activeEndpoints = apiEndpointRepository.findAll()
                .stream()
                .filter(ApiEndpoint::isActive)
                .toList();

        log.info("Running health check for {} endpoints", activeEndpoints.size());

        for (ApiEndpoint endpoint : activeEndpoints) {
            checkEndpoint(endpoint);
        }
    }

    private void checkEndpoint(ApiEndpoint endpoint) {
        long startTime = System.currentTimeMillis();
        boolean isUp = false;
        int statusCode = 0;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint.getUrl(), String.class);
            statusCode = response.getStatusCode().value();
            isUp = statusCode >= 200 && statusCode < 400;
        } catch (Exception e) {
            statusCode = 500;
            isUp = false;
        }

        long responseTime = System.currentTimeMillis() - startTime;

        HealthLog log = HealthLog.builder()
                .endpointId(endpoint.getId())
                .userId(endpoint.getUserId())
                .statusCode(statusCode)
                .responseTimeMs(responseTime)
                .isUp(isUp)
                .checkedAt(LocalDateTime.now())
                .build();

        healthLogRepository.save(log);
        this.log.info("Checked {} - UP: {} ({}ms)", endpoint.getUrl(), isUp, responseTime);
    }
}