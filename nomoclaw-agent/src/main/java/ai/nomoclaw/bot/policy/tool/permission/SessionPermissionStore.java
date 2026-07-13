package ai.nomoclaw.bot.policy.tool.permission;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionPermissionStore {

    private final Map<String, List<PermissionRule>> rulesByConversation = new ConcurrentHashMap<>();

    public List<PermissionRule> listRules(String conversationUid) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return List.of();
        }
        List<PermissionRule> rules = rulesByConversation.getOrDefault(conversationUid, List.of());
        if (rules.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<PermissionRule> active = rules.stream()
                .filter(rule -> rule != null && rule.enabled() && !rule.isExpired(now))
                .toList();
        if (active.size() != rules.size()) {
            rulesByConversation.put(conversationUid, new ArrayList<>(active));
        }
        return active;
    }

    public void addRule(String conversationUid, PermissionRule rule) {
        if (conversationUid == null || conversationUid.isBlank() || rule == null) {
            return;
        }
        rulesByConversation.compute(conversationUid, (key, existing) -> {
            List<PermissionRule> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.add(rule);
            return next;
        });
    }

    public void clearConversation(String conversationUid) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return;
        }
        rulesByConversation.remove(conversationUid);
    }
}
