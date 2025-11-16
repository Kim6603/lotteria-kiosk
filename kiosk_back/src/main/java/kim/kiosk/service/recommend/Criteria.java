package kim.kiosk.service.recommend;

public class Criteria {
    public String category;        // 버거/디저트/치킨/음료/아이스샷/사이드
    public boolean spicyWanted;    // 매운 선호
    public String keyword;         // 새우/치즈/불고기 등
    public Integer maxPrice;       // 최대 가격
    public Integer maxCalorie;     // 최대 칼로리
    public Integer minProtein;     // 최소 단백질
    public Integer maxSodium;      // 최대 나트륨(옵션)
    public String excludeAllergy;  // 제외할 알러지 키워드
}
