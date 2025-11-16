package kim.kiosk.service.ctx;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ClientPointerContext {

    private final ThreadLocal<Map<String, Object>> pointerTL =
            ThreadLocal.withInitial(LinkedHashMap::new);

    public void set(Map<String, Object> m) {
        pointerTL.get().clear();
        if (m != null) pointerTL.get().putAll(m);
    }

    public Map<String, Object> get() { return pointerTL.get(); }

    public void clear() { pointerTL.get().clear(); }
}
