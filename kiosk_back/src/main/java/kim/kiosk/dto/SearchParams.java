package kim.kiosk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchParams {
    private List<String> keywords;
    private Integer minPrice;
    private Integer maxPrice;
    private List<String> types;

    private Integer maxCalories;
    private Integer maxSodium;
    private Integer maxSugar;
    private Integer maxSaturatedFat;

    private Integer minWeight;
    private Integer maxWeight;

    private List<String> excludeAllergens;
    private Integer limit;
}
