package kim.kiosk.service.intent;

import lombok.Data;

@Data
public class LlmIntent {

    private String type;          // RECOMMEND, CART_ADD, CART_REMOVE, CART_CLEAR, TAB_SWITCH, NORMAL_CHAT, UNKNOWN
    private String menuName;      // 메뉴 이름(있으면)
    private String category;      // 버거/치킨/디저트/음료 등(있으면)
    private Integer quantity;     // 수량(있으면)
    private Boolean alternative;  // 대안 요청(또 다른 메뉴 추천해줘 등)

    public String typeUpper() {
        if (type == null) return "UNKNOWN";
        return type.trim().toUpperCase();
    }

    public boolean isAlternativeTrue() {
        return Boolean.TRUE.equals(alternative);
    }
}
