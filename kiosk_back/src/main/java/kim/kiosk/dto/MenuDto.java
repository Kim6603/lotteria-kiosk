package kim.kiosk.dto;

import lombok.Data;

@Data
public class MenuDto {
    private int id;
    private String name;
    private String type;
    private int price;
    private String description;
    private Integer calories;
    private String sodium;
    private String sugar;
    private String saturatedFat;
    private String totalWeight;
}
