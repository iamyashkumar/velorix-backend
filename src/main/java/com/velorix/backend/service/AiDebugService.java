package com.velorix.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velorix.backend.model.AiSuggestion;
import com.velorix.backend.model.LogEntry;
import com.velorix.backend.repository.AiSuggestionRepository;
import com.velorix.backend.repository.LogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiDebugService {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${groq.api.key}")
    private String groqApiKey;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";

    public AiSuggestion analyzeErrors(String userId, String endpointId) {
        // 1. Fetch last 20 ERROR logs
        List<LogEntry> errorLogs = logRepository.findByUserIdAndLevelAndTimestampAfter(
                userId, "ERROR", LocalDateTime.now().minusDays(1));

        if (errorLogs.isEmpty()) {
            throw new RuntimeException("No ERROR logs found in the last 24 hours.");
        }
        errorLogs = errorLogs.stream().limit(20).collect(Collectors.toList());

        // 2. Build prompt
        String errorMessages = errorLogs.stream()
                .map(log -> log.getTimestamp() + " - " + log.getMessage() + " [" + log.getSource() + "]")
                .collect(Collectors.joining("\n"));

        String prompt = "You are a senior backend engineer. Analyze these error logs and return ONLY a valid JSON object with exactly these 3 keys: possibleCause (string), recommendedFix (string), severity (HIGH, MEDIUM, or LOW). No markdown, no extra text, just raw JSON.\n\nError logs:\n" + errorMessages;

        // 3. Call Groq API
        String aiResponse = callGroqApi(prompt);

        // 4. Parse response
        Map<String, String> parsed = parseAiResponse(aiResponse);

        // 5. Save to MongoDB
        AiSuggestion suggestion = AiSuggestion.builder()
                .userId(userId)
                .endpointId(endpointId)
                .possibleCause(parsed.get("possibleCause"))
                .recommendedFix(parsed.get("recommendedFix"))
                .severity(parsed.get("severity"))
                .originalErrors(errorMessages.length() > 500 ? errorMessages.substring(0, 500) : errorMessages)
                .createdAt(LocalDateTime.now())
                .build();

        return aiSuggestionRepository.save(suggestion);
    }

    private String callGroqApi(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("messages", List.of(message));
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 300);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = mapper.readTree(response.getBody());
                return root.path("choices").get(0)
                        .path("message")
                        .path("content").asText();
            } else {
                throw new RuntimeException("Groq API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Groq API: " + e.getMessage());
        }
    }

    private Map<String, String> parseAiResponse(String raw) {
        Map<String, String> result = new HashMap<>();
        try {
            String jsonStr = raw.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
                int end = jsonStr.indexOf("```");
                if (end != -1) jsonStr = jsonStr.substring(0, end);
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
                int end = jsonStr.indexOf("```");
                if (end != -1) jsonStr = jsonStr.substring(0, end);
            }
            JsonNode node = mapper.readTree(jsonStr.trim());
            result.put("possibleCause", node.path("possibleCause").asText("Unknown cause"));
            result.put("recommendedFix", node.path("recommendedFix").asText("No fix provided"));
            result.put("severity", node.path("severity").asText("MEDIUM"));
        } catch (Exception e) {
            result.put("possibleCause", "Could not parse AI response");
            result.put("recommendedFix", raw.length() > 300 ? raw.substring(0, 300) : raw);
            result.put("severity", "MEDIUM");
        }
        return result;
    }
}