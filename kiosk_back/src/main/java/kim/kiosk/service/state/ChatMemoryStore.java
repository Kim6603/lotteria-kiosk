package kim.kiosk.service.state;

import kim.kiosk.service.recommend.Criteria;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMemoryStore {

    private final Map<String, LinkedHashSet<Integer>> servedBySession = new ConcurrentHashMap<>();
    private final Map<String, Criteria> lastCriteriaBySession = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> lastRecommendedBySession = new ConcurrentHashMap<>();

    public Map<String, LinkedHashSet<Integer>> getServedBySession() { return servedBySession; }
    public Map<String, Criteria> getLastCriteriaBySession() { return lastCriteriaBySession; }
    public Map<String, Map<String, Object>> getLastRecommendedBySession() { return lastRecommendedBySession; }
}
