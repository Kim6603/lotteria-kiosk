package kim.kiosk.service.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import kim.kiosk.service.ctx.ResponseMetaContext;
import kim.kiosk.service.intent.IntentDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UiFlowService {

    private final IntentDetector intentDetector;
    private final ResponseMetaContext responseMetaCtx;
    private final ObjectMapper objectMapper;

    public String switchTabFlow(String normalizedUserText) {
        String category = intentDetector.detectCategory(normalizedUserText);
        if (category == null) {
            responseMetaCtx.clear();
            return "어떤 탭으로 이동할지 말씀해 주세요.";
        }

        Map<String, Object> uiOps = new LinkedHashMap<>();
        uiOps.put("switchCategory", category);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("uiOps", uiOps);
        meta.put("switchCategory", category);
        meta.put("category", category);
        meta.put("lock", false);

        responseMetaCtx.clear();
        responseMetaCtx.putAll(meta);

        try {
            log.info("[AI][META] {}", objectMapper.writeValueAsString(meta));
        } catch (Exception ignore) {}

        return category + " 탭으로 이동할게요.";
    }
}
