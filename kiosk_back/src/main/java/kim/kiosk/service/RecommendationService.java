package kim.kiosk.service;

import kim.kiosk.domain.Menu;
import kim.kiosk.mapper.MenuMapper;
import kim.kiosk.service.ctx.ResponseMetaContext;
import kim.kiosk.service.recommend.Criteria;
import kim.kiosk.service.state.ChatMemoryStore;
import kim.kiosk.util.ChatDtoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MenuMapper menuMapper;
    private final ChatDtoUtil chatDtoUtil;

    private final ResponseMetaContext responseMetaCtx;
    private final ChatMemoryStore store;

    public String recommendFromDb(String sessionId, String userText, boolean askAlternative) {
        responseMetaCtx.clear();

        String normalized = chatDtoUtil.normalize(userText);

        Criteria now = parseCriteria(normalized);
        Criteria effective = askAlternative ? mergeCriteria(now, store.getLastCriteriaBySession().get(sessionId)) : now;

        if (chatDtoUtil.isBlank(effective.category)) effective.category = "버거";

        boolean spicyBeverageConflict = false;
        if (isBeverage(effective.category) && effective.spicyWanted) {
            spicyBeverageConflict = true;
            effective.spicyWanted = false;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("category", effective.category);
        params.put("keyword", effective.spicyWanted ? "매운" : effective.keyword);
        params.put("maxPrice", effective.maxPrice);
        params.put("maxCalorie", effective.maxCalorie);
        params.put("minProtein", effective.minProtein);
        params.put("maxSodium", effective.maxSodium);
        params.put("excludeAllergy", effective.excludeAllergy);

        List<Menu> candidates = Optional.ofNullable(menuMapper.findRecommendedMenu(params)).orElseGet(List::of);

        if (now.spicyWanted && !isBeverage(effective.category)) {
            List<Menu> spicyOnly = new ArrayList<>();
            for (Menu m : candidates) if (isMenuSpicy(m)) spicyOnly.add(m);
            if (!spicyOnly.isEmpty()) candidates = spicyOnly;
        }

        LinkedHashSet<Integer> seen = store.getServedBySession().computeIfAbsent(sessionId, k -> new LinkedHashSet<>());
        Menu picked = pickFirstNotSeen(candidates, seen, now, effective);

        if (picked == null) {
            List<Menu> all = Optional.ofNullable(menuMapper.findAllMenus()).orElseGet(List::of);
            List<Menu> filtered = new ArrayList<>();
            for (Menu m : all) {
                if (!categoryEquals(m, effective.category)) continue;
                if (now.spicyWanted && !isBeverage(effective.category) && !isMenuSpicy(m)) continue;
                if (!passesNumericFilters(m, effective)) continue;
                filtered.add(m);
            }
            picked = pickFirstNotSeen(filtered, seen, now, effective);
        }

        if (picked == null && isBeverage(effective.category)) {
            List<Menu> all = Optional.ofNullable(menuMapper.findAllMenus()).orElseGet(List::of);
            for (Menu m : all) {
                if (!categoryEquals(m, "음료")) continue;
                Integer id = chatDtoUtil.readInt(m, "id", "menuId", "menu_id", "ID", "Id");
                if (id != null && seen.contains(id)) continue;
                picked = m; break;
            }
        }

        if (picked == null) {
            if (spicyBeverageConflict) {
                responseMetaCtx.clear();
                return "음료에는 매운 맛 옵션이 없어요. 일반 음료로 추천을 도와드릴게요.";
            }
            responseMetaCtx.clear();
            return "추천할 메뉴를 찾지 못했습니다. 잠시 후 다시 시도해 주세요.";
        }

        Integer chosenId = chatDtoUtil.readInt(picked, "id", "menuId", "menu_id", "ID", "Id");
        if (chosenId != null) seen.add(chosenId);
        store.getLastCriteriaBySession().put(sessionId, effective);

        String reason = buildOneLineReason(picked, now, effective);

        String name = chatDtoUtil.firstNonEmpty(
                chatDtoUtil.readString(picked, "이름", "name", "menuName", "title"),
                "추천 메뉴");
        Integer priceVal = chatDtoUtil.readInt(picked, "가격", "price", "cost", "amount");
        int ensuredPrice = (priceVal != null) ? priceVal : 0;
        String priceTxt = chatDtoUtil.formatPrice(ensuredPrice) + "원";

        Map<String, Object> one = new LinkedHashMap<>();
        if (chosenId != null) one.put("id", chosenId);
        one.put("이름", name);
        one.put("name", name);
        one.put("가격", ensuredPrice);
        one.put("price", ensuredPrice);
        String type = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(picked, "종류", "type", "category"), effective.category);
        if (type != null) { one.put("종류", type); one.put("category", type); }
        String desc = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(picked, "설명", "description", "desc", "소개"), "");
        if (!desc.isBlank()) { one.put("설명", desc); one.put("description", desc); }
        if (!reason.isBlank()) one.put("reason", reason);
        one.put("imageUrl", "/images/menu/" + name + ".png");

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("recommendedMenus", List.of(one));
        if (chosenId != null) meta.put("recommendedMenuIds", List.of(chosenId));
        meta.put("lock", true);

        // 메타 로그 제거, 컨텍스트에만 저장
        responseMetaCtx.putAll(meta);
        store.getLastRecommendedBySession().put(sessionId, one);

        String prefix = spicyBeverageConflict ? "음료에는 매운 맛 옵션이 없어요. 대신 " : "";
        return prefix + name + " " + priceTxt + ". " + reason;
    }

    private Menu pickFirstNotSeen(List<Menu> candidates, Set<Integer> seen, Criteria now, Criteria effective) {
        for (Menu m : candidates) {
            if (!passesNumericFilters(m, effective)) continue;
            Integer id = chatDtoUtil.readInt(m, "id", "menuId", "menu_id", "ID", "Id");
            if (id != null && seen.contains(id)) continue;
            return m;
        }
        return null;
    }

    private boolean passesNumericFilters(Menu m, Criteria c) {
        Integer price = chatDtoUtil.readInt(m, "가격", "price", "cost", "amount");
        Integer kcal = chatDtoUtil.readInt(m, "열량", "칼로리", "calorie", "kcal");
        Integer protein = chatDtoUtil.readInt(m, "단백질", "protein", "prot");
        if (c.maxPrice != null && price != null && price > c.maxPrice) return false;
        if (c.maxCalorie != null && kcal != null && kcal > c.maxCalorie) return false;
        if (c.minProtein != null && protein != null && protein < c.minProtein) return false;
        return true;
    }

    private boolean isMenuSpicy(Menu m) {
        String type = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m, "종류", "type", "category"), "");
        if (isBeverage(type)) return false;
        String blob = (chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m, "이름", "name", "menuName", "title"), "") + " "
                + chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m, "설명", "description", "desc", "소개"), "")).toLowerCase(Locale.ROOT);
        if (blob.contains("불고기")) return false;
        return containsSpicy(blob);
    }

    private boolean isBeverage(String category) {
        if (category == null) return false;
        String c = category.toLowerCase(Locale.ROOT);
        return c.contains("음료") || c.contains("drink") || c.contains("beverage");
    }

    private boolean containsSpicy(String s) {
        if (s == null) return false;
        String[] keys = {"매운","매콤","스파이시","칠리","청양","화끈","불닭","레드핫","핫스파이시"};
        String ls = s.toLowerCase(Locale.ROOT);
        for (String k : keys) if (ls.contains(k)) return true;
        return false;
    }

    private boolean categoryEquals(Menu m, String cat) {
        String type = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m, "종류", "type", "category"), "");
        return type.equalsIgnoreCase(cat);
    }

    private Criteria mergeCriteria(Criteria now, Criteria prev) {
        if (prev == null) prev = new Criteria();
        Criteria r = new Criteria();
        r.category   = chatDtoUtil.firstNonEmpty(now.category, prev.category);
        r.keyword    = chatDtoUtil.firstNonEmpty(now.keyword, prev.keyword);
        r.excludeAllergy = chatDtoUtil.firstNonEmpty(now.excludeAllergy, prev.excludeAllergy);
        r.spicyWanted = now.spicyWanted || prev.spicyWanted;
        r.maxPrice   = (now.maxPrice   != null) ? now.maxPrice   : prev.maxPrice;
        r.maxCalorie = (now.maxCalorie != null) ? now.maxCalorie : prev.maxCalorie;
        r.minProtein = (now.minProtein != null) ? now.minProtein : prev.minProtein;
        r.maxSodium  = (now.maxSodium  != null) ? now.maxSodium  : prev.maxSodium;
        return r;
    }

    private Criteria parseCriteria(String t) {
        Criteria c = new Criteria();

        if (chatDtoUtil.containsAny(t, "아이스샷","아이스","차가운","시원한"))
            c.category = "아이스샷";
        else if (chatDtoUtil.containsAny(t, "치킨","핫윙","윙","치킨버켓"))
            c.category = "치킨";
        else if (chatDtoUtil.containsAny(t, "음료수","음료","마실","콜라","커피","에이드","쉐이크","베버리지","drink"))
            c.category = "음료";
        else if (chatDtoUtil.containsAny(t, "사이드","감자","포테이토","너겟","치즈스틱","side"))
            c.category = "사이드";
        else if (chatDtoUtil.containsAny(t, "디저트","아이스크림","디저","dessert"))
            c.category = "디저트";
        else if (chatDtoUtil.containsAny(t, "버거","햄버거","burger"))
            c.category = "버거";

        c.spicyWanted = chatDtoUtil.containsAny(t.replace("불고기", ""),
                "매운","맵","매콤","스파이시","칠리","화끈","청양","핫스파이시");

        if (chatDtoUtil.containsAny(t,"새우","shrimp")) c.keyword = "새우";
        else if (chatDtoUtil.containsAny(t,"치즈","cheese")) c.keyword = "치즈";
        else if (chatDtoUtil.containsAny(t,"불고기")) c.keyword = "불고기";

        c.maxPrice   = parseMaxPrice(t);
        c.maxCalorie = parseNumberNear(t, "칼로리","열량","kcal");
        c.minProtein = parseNumberNear(t, "단백질","프로틴");

        if (chatDtoUtil.containsAny(t,"우유 알레르기","유당불내증")) c.excludeAllergy = "우유";
        if (chatDtoUtil.containsAny(t,"대두 알레르기","콩 알레르기")) c.excludeAllergy = "대두";
        if (chatDtoUtil.containsAny(t,"밀 알레르기","글루텐")) c.excludeAllergy = "밀";

        return c;
    }

    private Integer parseMaxPrice(String t) {
        try {
            if (t.contains("만원")) return 10000;
            if (t.contains("천원")) {
                String n = t.substring(0, t.indexOf("천원")).replaceAll("[^0-9]", "");
                if (!n.isEmpty()) return Integer.parseInt(n) * 1000;
            }
            if (t.contains("천")) {
                String n = t.substring(0, t.indexOf("천")).replaceAll("[^0-9]", "");
                if (!n.isEmpty()) return Integer.parseInt(n) * 1000;
            }
            var m = java.util.regex.Pattern.compile("(\\d{3,6})\\s*원?").matcher(t);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignore) {}
        return null;
    }

    private Integer parseNumberNear(String t, String... hints) {
        for (String h : hints) {
            if (t.contains(h)) {
                var m = java.util.regex.Pattern.compile("(\\d{2,4})").matcher(t);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
        }
        return null;
    }

    private String buildOneLineReason(Menu m, Criteria now, Criteria effective) {
        String name = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m,"이름","name","menuName","title"),"");
        String desc = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m,"설명","description","desc","소개"),"");
        String type = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m,"종류","type","category"), effective.category);
        String recommendFlag = chatDtoUtil.firstNonEmpty(chatDtoUtil.readString(m,"추천","isRecommended","recommend"),"");
        Integer price = chatDtoUtil.readInt(m,"가격","price","cost","amount");
        Integer kcal = chatDtoUtil.readInt(m,"열량","칼로리","calorie","kcal");
        Integer protein = chatDtoUtil.readInt(m,"단백질","protein","prot");
        Integer sodium = chatDtoUtil.readInt(m,"나트륨","sodium");
        Integer sugar = chatDtoUtil.readInt(m,"당류","sugar");
        String blob = (name + " " + desc).toLowerCase(Locale.ROOT);

        boolean beverage = isBeverage(type);

        if (now.spicyWanted && !beverage && containsSpicy(blob))
            return "매콤한 풍미가 분명해 자극적으로 즐기기 좋아요.";
        if (effective.maxPrice != null && price != null && price <= effective.maxPrice)
            return "예산을 지키면서 만족감을 주는 가성비 선택이에요.";
        if (effective.maxCalorie != null && kcal != null && kcal <= effective.maxCalorie)
            return "칼로리 부담을 줄여 가볍게 즐기기 좋아요.";
        if (effective.minProtein != null && protein != null && protein >= effective.minProtein)
            return "단백질이 든든해 포만감이 오래가요.";

        if (beverage) {
            if (chatDtoUtil.containsAny(blob, "아메리카노","coffee","커피","라떼","카페"))
                return "깔끔한 맛으로 식사와 함께 마시기 좋아요.";
            if (chatDtoUtil.containsAny(blob, "에이드","레몬","자몽","청포도","복숭아"))
                return "상큼하게 입가심하기 좋아요.";
            if (chatDtoUtil.containsAny(blob, "콜라","사이다","제로","탄산"))
                return "탄산의 청량감이 느끼함을 잡아줘요.";
            if (chatDtoUtil.containsAny(blob, "초코","핫초코","코코아"))
                return "달콤하고 부드러워 디저트로 잘 어울려요.";
            if (kcal != null && kcal <= 200) return "가볍게 마시기 좋아 부담이 적어요.";
            return "시원하게 갈증을 풀어주기 좋아요.";
        }

        if (chatDtoUtil.containsAny(blob, "불고기"))
            return pickOne("달짝지근한 소스와 부드러운 패티 조합이 호불호 없이 잘 맞아요.",
                    "한국형 입맛에 잘 맞는 달고 짭짤한 풍미가 포인트예요.");
        if (chatDtoUtil.containsAny(blob, "새우","통새우","shrimp"))
            return pickOne("바삭한 튀김과 새우 식감이 조화를 이뤄 만족스러워요.",
                    "새우의 고소한 풍미가 한 입마다 살아있어요.");
        if (chatDtoUtil.containsAny(blob, "치즈","모짜","cheese"))
            return pickOne("진한 치즈 풍미로 고소하게 든든해요.",
                    "치즈가 듬뿍 들어가 한입마다 만족감이 커요.");

        if ("O".equalsIgnoreCase(recommendFlag) || chatDtoUtil.containsAny(blob,"인기","베스트","signature","시그니처"))
            return pickOne("재구매가 많은 메뉴라 실패 없는 선택이에요.",
                    "후기가 좋아 믿고 고르기 좋다는 평가가 많아요.");

        if (price != null && price >= 7000) return "두툼한 구성으로 한 끼 식사로 손색이 없어요.";
        if (sodium != null && sodium <= 700) return "짜지 않아 부담 없이 즐기기 좋아요.";
        if (sugar != null && sugar <= 10) return "단맛이 과하지 않아 깔끔하게 즐길 수 있어요.";

        return pickOne("맛과 식감의 밸런스가 좋아 누구나 편하게 즐기기 좋아요.",
                "소스와 재료 조합이 조화로워 만족도가 높아요.",
                "풍미가 뚜렷해 첫 입부터 존재감이 느껴져요.");
    }

    private String pickOne(String... arr) {
        if (arr == null || arr.length == 0) return "";
        int idx = Math.abs(Objects.hashCode(Arrays.toString(arr) + System.nanoTime())) % arr.length;
        return arr[idx];
    }
}
