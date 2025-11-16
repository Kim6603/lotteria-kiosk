package kim.kiosk.service;

import kim.kiosk.domain.Menu;
import kim.kiosk.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final MenuMapper menuMapper;

    public List<Menu> findType(String type) {
        return menuMapper.findByType(type);
    }

    public Menu findById(int id){
        return menuMapper.findById(id);
    }
}
