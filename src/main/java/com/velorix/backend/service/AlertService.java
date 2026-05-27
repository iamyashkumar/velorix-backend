package com.velorix.backend.service;

import com.velorix.backend.model.AlertRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AlertService {

    @Autowired
    private EmailAlertService emailAlertService;  // 🔥 injected

    public void sendAlert(AlertRule rule, String message) {
        System.out.println("🔔 ALERT for user " + rule.getUserId() + ": " + message);

        List<String> channels = rule.getChannels();
        if (channels == null || channels.isEmpty()) {
            System.out.println("   → No channels configured for this rule.");
            return;
        }

        for (String channel : channels) {
            if ("email".equalsIgnoreCase(channel)) {
                // ✅ Now actually sends email
                emailAlertService.sendEmail(rule.getEmail(), "Velorix Alert", message);
            } else if ("telegram".equalsIgnoreCase(channel)) {
                System.out.println("   → Telegram not yet implemented: " + rule.getTelegramChatId());
            } else {
                System.out.println("   → Unknown channel: " + channel);
            }
        }
    }
}