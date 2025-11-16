package kim.kiosk.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import kim.kiosk.domain.Menu;
import kim.kiosk.service.MenuService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;

    @Data
    public static class MenuRequest {
        @JsonProperty("종류")
        @JsonAlias({"type", "category"})
        private String 종류;

        @JsonProperty("id")
        @JsonAlias({"menuId", "menu_id", "ID", "Id"})
        private Integer id;
    }

    @PostMapping("/type")
    public List<Menu> type(@RequestBody MenuRequest request) {
        final String rawType = request.get종류();
        final String resolvedType = (rawType == null || rawType.isBlank()) ? "버거" : rawType.trim();

        log.info("[/api/type] req.종류='{}' → resolvedType='{}'", rawType, resolvedType);

        return Optional.ofNullable(menuService.findType(resolvedType))
                .orElseGet(List::of);
    }

    @PostMapping("/product")
    public Menu name(@RequestBody MenuRequest request) {
        final Integer id = request.getId();
        log.info("[/api/product] req.id={}", id);

        if (id == null) {
            return null;
        }
        return menuService.findById(id);
    }
}
