package kim.kiosk.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRecommendResponse {

    private String message;
    private List<Combo> combos;

    @Data
    public static class Combo {
        private List<Item> items;
        private Integer totalPrice;
    }

    @Data
    public static class Item {
        private Integer id;
        private String name;
        private String type;
        private Integer price;
    }
}
