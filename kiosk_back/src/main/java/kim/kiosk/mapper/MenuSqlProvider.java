package kim.kiosk.mapper;

import kim.kiosk.dto.SearchParams;

import java.util.List;
import java.util.Map;

public class MenuSqlProvider {

    public String buildSearch(Map<String, Object> param) {
        SearchParams p = (SearchParams) param.get("p");
        StringBuilder sb = new StringBuilder();
        sb.append("""
            SELECT id,
                   이름 AS name,
                   종류 AS type,
                   가격 AS price,
                   설명 AS description,
                   총중량 AS totalWeight,
                   열량 AS calories,
                   나트륨 AS sodium,
                   당류 AS sugar,
                   포화지방 AS saturatedFat,
                   `알러지정보` AS allergens
            FROM menu
            WHERE 1=1
            """);

        if (p.getKeywords() != null && !p.getKeywords().isEmpty()) {
            List<String> kws = p.getKeywords();
            for (int i = 0; i < kws.size(); i++) {
                sb.append(" AND (이름 LIKE CONCAT('%', #{p.keywords[")
                        .append(i).append("]}, '%') ")
                        .append("OR 설명 LIKE CONCAT('%', #{p.keywords[")
                        .append(i).append("]}, '%') ")
                        .append("OR 종류 LIKE CONCAT('%', #{p.keywords[")
                        .append(i).append("]}, '%')) ");
            }
        }

        if (p.getMinPrice() != null) sb.append(" AND 가격 >= #{p.minPrice} ");
        if (p.getMaxPrice() != null) sb.append(" AND 가격 <= #{p.maxPrice} ");

        if (p.getTypes() != null && !p.getTypes().isEmpty()) {
            sb.append(" AND 종류 IN (");
            for (int i = 0; i < p.getTypes().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("#{p.types[").append(i).append("]}");
            }
            sb.append(") ");
        }

        if (p.getMaxCalories() != null)     sb.append(" AND 열량 <= #{p.maxCalories} ");
        if (p.getMaxSodium() != null)       sb.append(" AND 나트륨 <= #{p.maxSodium} ");
        if (p.getMaxSugar() != null)        sb.append(" AND 당류 <= #{p.maxSugar} ");
        if (p.getMaxSaturatedFat() != null) sb.append(" AND 포화지방 <= #{p.maxSaturatedFat} ");
        if (p.getMinWeight() != null)       sb.append(" AND 총중량 >= #{p.minWeight} ");
        if (p.getMaxWeight() != null)       sb.append(" AND 총중량 <= #{p.maxWeight} ");

        if (p.getExcludeAllergens() != null && !p.getExcludeAllergens().isEmpty()) {
            for (int i = 0; i < p.getExcludeAllergens().size(); i++) {
                sb.append(" AND (`알러지정보` IS NULL OR `알러지정보` NOT LIKE CONCAT('%', #{p.excludeAllergens[")
                        .append(i).append("]}, '%')) ");
            }
        }

        sb.append(" ORDER BY id ");
        sb.append(" LIMIT ").append(p.getLimit() == null ? 50 : "#{p.limit}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public String buildByIds(Map<String, Object> param) {
        List<Integer> ids = (List<Integer>) param.get("ids");
        if (ids == null || ids.isEmpty()) {
            return "SELECT id, 이름 AS name, 종류 AS type, 가격 AS price, 설명 AS description, 총중량 AS totalWeight, 열량 AS calories, 나트륨 AS sodium, 당류 AS sugar, 포화지방 AS saturatedFat, `알러지정보` AS allergens FROM menu WHERE 1=0";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("""
            SELECT id,
                   이름 AS name,
                   종류 AS type,
                   가격 AS price,
                   설명 AS description,
                   총중량 AS totalWeight,
                   열량 AS calories,
                   나트륨 AS sodium,
                   당류 AS sugar,
                   포화지방 AS saturatedFat,
                   `알러지정보` AS allergens
            FROM menu
            WHERE id IN (
            """);
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("#{ids[").append(i).append("]}");
        }
        sb.append(") ORDER BY id");
        return sb.toString();
    }
}
