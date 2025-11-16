package kim.kiosk.service;

import kim.kiosk.domain.Menu;
import kim.kiosk.service.ctx.ResponseMetaContext;
import kim.kiosk.service.resolution.TargetResolver;
import kim.kiosk.service.state.ChatMemoryStore;
import kim.kiosk.util.ChatDtoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartFlowService {

    private final ChatDtoUtil chatDtoUtil;
    private final ResponseMetaContext responseMetaCtx;
    private final TargetResolver targetResolver;
    private final ChatMemoryStore store;

    public String addToCartFlow(String sessionId, String userText) {
        int qty = parseQuantity(userText);
        Menu target = targetResolver.resolveTargetMenu(sessionId, userText);

        if (target == null) {
            responseMetaCtx.clear();
            return "어떤 메뉴를 담을지 정확히 못 알아들었어요. 메뉴 이름을 한 번만 더 말해 주세요.";
        }

        Integer id = chatDtoUtil.readInt(target, "id", "menuId", "menu_id", "ID", "Id");
        String name = chatDtoUtil.firstNonEmpty(
                chatDtoUtil.readString(target, "이름", "name", "menuName", "title"), "선택한 메뉴");
        Integer price = chatDtoUtil.readInt(target, "가격", "price", "cost", "amount");
        int p = (price != null) ? price : 0;

        Map<String, Object> addItem = new LinkedHashMap<>();
        if (id != null) addItem.put("id", id);
        addItem.put("이름", name);
        addItem.put("name", name);
        addItem.put("가격", p);
        addItem.put("price", p);
        addItem.put("count", qty);
        addItem.put("imageUrl", "/images/menu/" + name + ".png");

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cartOps", Map.of("add", List.of(addItem)));
        meta.put("lock", false);

        responseMetaCtx.clear();
        responseMetaCtx.putAll(meta);

        store.getLastRecommendedBySession().put(sessionId, addItem);

        return name + " " + qty + "개 장바구니에 담았어요.";
    }

    public String removeFromCartFlow(String sessionId, String userText) {
        Menu target = targetResolver.resolveTargetMenu(sessionId, userText);
        if (target == null) {
            responseMetaCtx.clear();
            return "어떤 메뉴를 뺄지 정확히 못 알아들었어요. 메뉴 이름을 한 번만 더 말해 주세요.";
        }

        Integer id = chatDtoUtil.readInt(target, "id", "menuId", "menu_id", "ID", "Id");
        String name = chatDtoUtil.firstNonEmpty(
                chatDtoUtil.readString(target, "이름", "name", "menuName", "title"), "선택한 메뉴");
        Integer price = chatDtoUtil.readInt(target, "가격", "price", "cost", "amount");
        int p = (price != null) ? price : 0;

        boolean all = isAllOfThatItem(chatDtoUtil.normalize(userText));
        Map<String, Object> meta = new LinkedHashMap<>();

        if (all) {
            Map<String, Object> one = new LinkedHashMap<>();
            if (id != null) one.put("id", id);
            one.put("이름", name);
            one.put("name", name);
            meta.put("cartOps", Map.of("removeAll", List.of(one)));

            responseMetaCtx.clear();
            responseMetaCtx.putAll(meta);
            store.getLastRecommendedBySession().put(sessionId, one);
            return name + " 전부 장바구니에서 뺐어요.";
        } else {
            int qty = parseQuantity(userText);
            Map<String, Object> one = new LinkedHashMap<>();
            if (id != null) one.put("id", id);
            one.put("이름", name);
            one.put("name", name);
            one.put("가격", p);
            one.put("price", p);
            one.put("count", qty);
            meta.put("cartOps", Map.of("remove", List.of(one)));

            responseMetaCtx.clear();
            responseMetaCtx.putAll(meta);
            store.getLastRecommendedBySession().put(sessionId, one);
            return name + " " + qty + "개 장바구니에서 뺐어요.";
        }
    }

    public String clearCartFlow() {
        Map<String, Object> meta = new LinkedHashMap<>();
        Map<String, Object> ops = new LinkedHashMap<>();
        ops.put("clear", true);
        meta.put("cartOps", ops);
        meta.put("lock", false);

        responseMetaCtx.clear();
        responseMetaCtx.putAll(meta);
        return "장바구니를 모두 비웠어요.";
    }

    private int parseQuantity(String t) {
        if (t == null) return 1;
        Map<String, Integer> wordNum = Map.ofEntries(
                Map.entry("한", 1), Map.entry("하나", 1), Map.entry("한개", 1), Map.entry("1", 1),
                Map.entry("두", 2), Map.entry("둘", 2), Map.entry("두개", 2), Map.entry("2", 2),
                Map.entry("세", 3), Map.entry("셋", 3), Map.entry("세개", 3), Map.entry("3", 3),
                Map.entry("네", 4), Map.entry("넷", 4), Map.entry("네개", 4), Map.entry("4", 4),
                Map.entry("다섯", 5), Map.entry("5", 5), Map.entry("여섯", 6), Map.entry("6", 6)
        );
        String s = t.replaceAll("\\s+", "");
        for (var e : wordNum.entrySet()) {
            if (s.contains(e.getKey() + "개") || s.contains(e.getKey() + "잔") || s.contains(e.getKey()))
                return Math.max(1, Math.min(9, e.getValue()));
        }
        var m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*(개|잔)?").matcher(t);
        if (m.find()) {
            try { return Math.max(1, Math.min(9, Integer.parseInt(m.group(1)))); } catch (Exception ignore) {}
        }
        return 1;
    }

    private boolean isAllOfThatItem(String t) {
        if (t == null) return false;
        return chatDtoUtil.containsAny(t, "전부","모두","다","전체");
    }
}
