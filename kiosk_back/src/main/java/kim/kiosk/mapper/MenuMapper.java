package kim.kiosk.mapper;

import kim.kiosk.domain.Menu;
import kim.kiosk.dto.MenuDto;
import kim.kiosk.dto.SearchParams;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

@Mapper
public interface MenuMapper {

    @Select("select id, 이름, 가격, 종류 from menu where 종류 = #{종류}")
    List<Menu> findByType(@Param("종류") String 종류);

    @Select("select * from menu where id = #{id}")
    Menu findById(@Param("id") int id);

    List<Menu> findRecommendedMenu(Map<String, Object> params);

    List<MenuDto> searchMenus(SearchParams params);
    List<MenuDto> getMenusByIds(List<Integer> ids);

    List<Menu> findAllMenus();
}