package kim.kiosk.domain;

import lombok.Data;

@Data
public class Menu {
    private int id;
    private String 이름;
    private String 종류;
    private int 가격;
    private String 설명;
    private String 추천;
    private int 총중량;
    private int 열량;
    private String 단백질;
    private String 나트륨;
    private String 당류;
    private String 포화지방;
    private String 알러지정보;
}
