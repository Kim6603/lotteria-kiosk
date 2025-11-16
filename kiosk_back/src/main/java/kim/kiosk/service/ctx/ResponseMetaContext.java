package kim.kiosk.service.ctx;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ResponseMetaContext {

    private final ThreadLocal<Map<String, Object>> metaTL =
            ThreadLocal.withInitial(LinkedHashMap::new);

    public Map<String, Object> map() { return metaTL.get(); }

    public void putAll(Map<String, Object> m) { if (m != null) metaTL.get().putAll(m); }

    public Map<String, Object> snapshot() { return new LinkedHashMap<>(metaTL.get()); }

    public void clear() { metaTL.get().clear(); }
}
