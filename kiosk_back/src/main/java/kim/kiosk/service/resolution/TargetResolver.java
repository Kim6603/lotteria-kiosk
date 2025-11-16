package kim.kiosk.service.resolution;

import kim.kiosk.domain.Menu;
import kim.kiosk.mapper.MenuMapper;
import kim.kiosk.service.ctx.ClientPointerContext;
import kim.kiosk.service.state.ChatMemoryStore;
import kim.kiosk.util.ChatDtoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TargetResolver {

    private final MenuMapper menuMapper;
    private final ChatDtoUtil chatDtoUtil;
    private final ClientPointerContext clientPointerCtx;
    private final ChatMemoryStore store;

    public Menu resolveTargetMenu(String sessionId, String userText) {
        String lower = userText == null ? "" : chatDtoUtil.normalize(userText);
        boolean deictic = hasDeictic(lower);
        List<Menu> all = Optional.ofNullable(menuMapper.findAllMenus()).orElseGet(List::of);

        if (deictic) {
            Map<String, Object> cp = clientPointerCtx.get();
            if (cp != null && !cp.isEmpty()) {
                Integer cid = chatDtoUtil.coerceToInt(cp.get("id"));
                String cname = cp.get("name") != null ? String.valueOf(cp.get("name")) : null;
                String ccat = cp.get("category") != null ? String.valueOf(cp.get("category")) : null;

                Menu byId = findById(all, cid);
                if (byId != null) {
                    log.info("[AI][RESOLVE] deictic=clientPointer id={} → {}",
                            cid, chatDtoUtil.readString(byId, "이름","name","menuName","title"));
                    return byId;
                }
                Menu byName = findByExactName(all, cname, ccat);
                if (byName != null) {
                    log.info("[AI][RESOLVE] deictic=clientPointer name='{}' cat='{}' → {}",
                            cname, ccat, chatDtoUtil.readString(byName,"이름","name","menuName","title"));
                    return byName;
                }
            }
        }

        if (deictic) {
            var last = store.getLastRecommendedBySession().get(sessionId);
            if (last != null) {
                Integer lid = chatDtoUtil.coerceToInt(last.get("id"));
                String lname = String.valueOf(firstNonNull(last.get("이름"), last.get("name")));
                String lcat = (last.get("종류") != null) ? String.valueOf(last.get("종류"))
                        : (last.get("category") != null ? String.valueOf(last.get("category")) : null);

                Menu byId = findById(all, lid);
                if (byId != null) {
                    log.info("[AI][RESOLVE] deictic=serverLast id={} → {}",
                            lid, chatDtoUtil.readString(byId, "이름","name","menuName","title"));
                    return byId;
                }
                Menu byExact = findByExactName(all, lname, lcat);
                if (byExact != null) {
                    log.info("[AI][RESOLVE] deictic=serverLast name='{}' cat='{}' → {}",
                            lname, lcat, chatDtoUtil.readString(byExact,"이름","name","menuName","title"));
                    return byExact;
                }
            }
            log.info("[AI][RESOLVE] deictic but no match → ask user again");
            return null;
        }

        return findBestMenuByTokens(lower, all);
    }

    private boolean hasDeictic(String lower) {
        return chatDtoUtil.containsAny(lower, "이거","그거","추천한","추천해준","방금","지금거","그 메뉴","지금거");
    }

    private Object firstNonNull(Object... arr) {
        if (arr == null) return null;
        for (Object o : arr) if (o != null) return o;
        return null;
    }

    private String normName(String s) {
        if (s == null) return null;
        return s.replaceAll("\\s+","").trim().toLowerCase(Locale.ROOT);
    }

    private Menu findById(List<Menu> all, Integer id) {
        if (id == null) return null;
        for (Menu m : all) {
            Integer mid = chatDtoUtil.readInt(m,"id","menuId","menu_id","ID","Id");
            if (mid != null && mid.equals(id)) return m;
        }
        return null;
    }

    private Menu findByExactName(List<Menu> all, String name, String preferCategory) {
        if (name == null || name.isBlank()) return null;
        String target = normName(name);
        Menu first = null;
        for (Menu m : all) {
            String nm = chatDtoUtil.firstNonEmpty(
                    chatDtoUtil.readString(m,"이름","name","menuName","title"), "");
            if (target.equals(normName(nm))) {
                if (preferCategory == null || preferCategory.isBlank()) return m;
                String cat = chatDtoUtil.firstNonEmpty(
                        chatDtoUtil.readString(m,"종류","type","category"), "");
                if (cat.equalsIgnoreCase(preferCategory)) return m;
                if (first == null) first = m;
            }
        }
        return first;
    }

    private Menu findBestMenuByTokens(String text, List<Menu> all) {
        if (text == null) return null;
        String cleaned = text.replaceAll("[^가-힣a-zA-Z0-9]", " ")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return null;

        Set<String> drop = Set.of("버거","세트","세트로","메뉴","롯데리아","리아","음료","사이드","디저트","치킨","아이스샷");
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (String tk : cleaned.split(" ")) {
            if (tk.length() < 2) continue;
            if (drop.contains(tk)) continue;
            tokens.add(tk.toLowerCase(Locale.ROOT));
        }
        if (tokens.isEmpty()) tokens = java.util.List.of(cleaned.toLowerCase(Locale.ROOT));

        int bestScore = -1;
        Menu best = null;
        for (Menu m : all) {
            String name = chatDtoUtil.firstNonEmpty(
                    chatDtoUtil.readString(m,"이름","name","menuName","title"), "");
            String hay = (name + " " + chatDtoUtil.firstNonEmpty(
                    chatDtoUtil.readString(m,"설명","description","desc","소개"), "")
            ).toLowerCase(Locale.ROOT).replace(" ", "");
            int score = 0;
            for (String tk : tokens) {
                String q = tk.replace(" ","");
                if (q.length() >= 2 && hay.contains(q)) score += q.length();
            }
            if (hay.contains(cleaned.replace(" ",""))) score += 10;
            if (score > bestScore) { bestScore = score; best = m; }
        }
        return bestScore > 0 ? best : null;
    }
}
