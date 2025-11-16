package kim.kiosk.service.intent;

import kim.kiosk.service.state.ChatMemoryStore;
import kim.kiosk.util.ChatDtoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentDetector {

    private final ChatDtoUtil chatDtoUtil;
    private final ChatMemoryStore store;

    public boolean isCartAddIntent(String t) {
        if (t == null) return false;
        return chatDtoUtil.containsAny(t,
                "담아","담기","담아줘","담아 줘","담아놔","담아 놔","담아둬","담아 둬",
                "장바구니","카트","추가","넣어","넣어줘","넣어 줘");
    }
    public boolean isCartRemoveIntent(String t) {
        if (t == null) return false;
        return chatDtoUtil.containsAny(t,
                "빼줘","빼 줘","빼","제거","삭제","지워","지워줘","지워 줘",
                "없애","없애줘","없애 줘","빼라","빼주세요","삭제해","삭제 해");
    }
    public boolean isCartClearIntent(String t) {
        if (t == null) return false;
        return chatDtoUtil.containsAny(t,
                "장바구니 비워","비워줘","비워 줘","다 비워","싹 비워","비워라",
                "전체 삭제","모두 삭제","전부 삭제","올 클리어","카트 비워","카트 비워줘","장바구니 초기화");
    }

    public boolean isTabSwitchIntent(String t) {
        if (t == null) return false;
        String cat = detectCategory(t);
        if (cat == null) return false;

        boolean explicitVerb = chatDtoUtil.containsAny(t,
                "보여","보여줘","보여 줘","이동","로 이동","로 가","가줘","가 줘",
                "탭","카테고리","메뉴","뭐 있어","뭐있어","알려줘","알려 줘","목록","리스트");
        boolean desireVerb = chatDtoUtil.containsAny(t,
                "먹고싶","먹고 싶","먹고싶은데","먹고 싶은데","먹자","먹을래",
                "땡기","생각나","생각 나","보고싶","보고 싶","보러","볼래",
                "찾아","찾고","찾아줘","찾아 줘","가자","가고싶","가고 싶");
        boolean hasRecommendCue = chatDtoUtil.containsAny(t,
                "추천","골라","고르","추천해","추천 해",
                "추천해줘","추천 해줘","추천해 줄래","추천 해 줄래","뭐 먹");

        boolean intent = (explicitVerb || desireVerb) && !hasRecommendCue;
        log.info("[AI][INTENT] tabSwitch={} category={} explicitVerb={} desireVerb={} recommendCue={}",
                intent ? "TRUE":"FALSE", cat, explicitVerb, desireVerb, hasRecommendCue);
        return intent;
    }

    public String detectCategory(String t) {
        if (t == null) return null;
        if (chatDtoUtil.containsAny(t, "아이스샷","아이스 샷","아이스","차가운","시원한")) return "아이스샷";
        if (chatDtoUtil.containsAny(t, "치킨","핫윙","윙","치킨버켓","치킨메뉴")) return "치킨";
        if (chatDtoUtil.containsAny(t, "음료수","음료","마실","drink","콜라","사이다","탄산","제로","커피","라떼","에이드","쉐이크","스무디"))
            return "음료";
        if (chatDtoUtil.containsAny(t, "디저트","디저","dessert","아이스크림")) return "디저트";
        if (chatDtoUtil.containsAny(t, "버거","햄버거","burger")) return "버거";
        return null;
    }

    public boolean isMenuRecommendIntent(String t) {
        if (t == null) return false;
        if (chatDtoUtil.containsAny(t,
                "추천","메뉴 추천","뭐 먹","뭐가 좋","골라","고르","버거 추천",
                "음료 추천","음료수 추천","사이드 추천","디저트 추천","스파이시 추천","매운 메뉴",
                "추천해줘","추천 해줘","추천해 줄래","추천 해 줄래")) return true;
        return chatDtoUtil.containsAny(t, "버거","음료","음료수","사이드","디저트","치킨","아이스샷")
                && chatDtoUtil.containsAny(t,"해줘","해 줘","골라줘","골라 줘","추천해","추천 해");
    }

    public boolean isAlternativeFollowup(String sessionId, String t) {
        boolean hasPrev = store.getLastCriteriaBySession().containsKey(sessionId);
        boolean altPattern = t.matches(".*(다른|말고|또).*(없|줄래|추천|있|줘).*")
                || chatDtoUtil.containsAny(t,"다른거","다른 메뉴","다른건","다른 건","또 추천");
        return hasPrev && altPattern;
    }
}
