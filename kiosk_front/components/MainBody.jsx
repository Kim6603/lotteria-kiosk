import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";

function MainBody({ aiMode = false, aiItems = [], forcedList = null, forcedCategory = null, onClearAiLock }) {
  const [activeCategory, selectCategory] = useState("버거");
  const [menuList, setMenuList] = useState([]);
  const categorys = ["버거", "디저트", "치킨", "음료", "아이스샷"];
  const navigate = useNavigate();

  const clearRef = useRef(onClearAiLock);
  useEffect(() => { clearRef.current = onClearAiLock; }, [onClearAiLock]);

  const setType = async (menu) => {
    const response = await fetch("/api/type", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ 종류: menu }),
    });
    const data = await response.json();
    setMenuList(data);
  };

  const productHandle = async (id) => {
    const response = await fetch("/api/product", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id }),
    });
    const data = await response.json();
    navigate(`/menu/${id}`, { state: { menu: data } });
  };

  const resolveAndOpenByName = async (name) => {
    const norm = (s) => (s || "").replace(/\s+/g, "").trim();
    const target = norm(name);
    console.group("[AI][MainBody] resolveByName");
    console.log("target name:", name);

    const findIn = (arr) => {
      for (const it of arr || []) {
        const nm = it?.이름 ?? it?.name ?? it?.menuName ?? it?.title;
        const id = it?.id ?? it?.menuId ?? it?.menu_id ?? it?.ID ?? it?.Id;
        if (id && norm(nm) === target) return id;
      }
      return null;
    };

    let found = findIn(menuList);
    if (found) { console.log("found in current list:", found); console.groupEnd(); return productHandle(found); }

    for (const cat of categorys) {
      const res = await fetch("/api/type", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ 종류: cat }),
      });
      const list = await res.json();
      const id = findIn(list);
      if (id) {
        console.log("found in category:", cat, "id:", id);
        console.groupEnd();
        return productHandle(id);
      }
    }
    console.warn("not found by name:", name);
    console.groupEnd();
    alert("상세 정보를 찾을 수 없습니다. 잠시 후 다시 시도해 주세요.");
  };

  const categoryHandle = async (category) => {
    if (typeof clearRef.current === "function") {
      clearRef.current();
      console.log("[AI][MainBody] 카테고리 클릭으로 AI 모드/목록 강제 해제:", category);
    }
    selectCategory(category);
    await setType(category);
  };

  useEffect(() => { setType("버거"); }, []);

  const normAi = (it) => ({
    id: it?.id ?? it?.menuId ?? it?.menu_id ?? it?.ID ?? it?.Id,
    이름: it?.이름 ?? it?.name ?? it?.menuName ?? it?.title ?? "",
    가격: it?.가격 ?? it?.price ?? it?.cost ?? it?.amount ?? 0,
    imageUrl: it?.imageUrl,
    category: it?.종류 ?? it?.type ?? it?.category,
  });

  useEffect(() => {
    if (Array.isArray(forcedList) && forcedList.length) {
      const normalized = forcedList
        .map(it => ({
          id: it?.id ?? it?.menuId ?? it?.menu_id ?? it?.ID ?? it?.Id,
          이름: it?.이름 ?? it?.name ?? it?.menuName ?? it?.title ?? "",
          가격: Number(it?.가격 ?? it?.price ?? it?.cost ?? it?.amount ?? 0) || 0,
          imageUrl: it?.imageUrl
        }))
        .filter(x => x.이름 || x.id);
      if (forcedCategory && categorys.includes(forcedCategory)) {
        selectCategory(forcedCategory);
      }
      setMenuList(normalized);
      console.log("[AI][MainBody] forced list applied:", { cat: forcedCategory, size: normalized.length });
    }
  }, [forcedList, forcedCategory]);

  const viewItems = aiMode
    ? (aiItems && aiItems.length ? [normAi(aiItems[0])] : [])
    : menuList;

  useEffect(() => {
    console.group("[AI][MainBody] render");
    console.log("aiMode:", aiMode, "aiItem:", aiItems?.[0]);
    console.log("activeCategory:", activeCategory, "viewItem0:", viewItems?.[0]);
    console.groupEnd();
    try { sessionStorage.setItem("lastViewItems0@MainBody", JSON.stringify(viewItems?.[0] || null)); } catch {}
  }, [aiMode, aiItems, activeCategory, viewItems]);

  useEffect(() => {
    const valid = new Set(categorys);

    const applySwitch = async (cat) => {
      if (!cat || !valid.has(cat)) { console.debug("[AI][MainBody] 무시된 탭:", cat); return; }
      if (typeof clearRef.current === "function") clearRef.current();
      selectCategory(cat);
      await setType(cat);
      try { sessionStorage.removeItem("aiSwitchCategory"); } catch {}
      console.info("[AI][MainBody] ▶ ai-switch-category 적용:", cat);
    };

    try {
      const ss = sessionStorage.getItem("aiSwitchCategory");
      if (ss && valid.has(ss)) { applySwitch(ss); }
    } catch {}

    const handler = (e) => {
      const cat = e?.detail?.category;
      if (valid.has(cat)) applySwitch(cat);
      else console.debug("[AI][MainBody] ai-switch-category 수신했지만 유효하지 않음:", cat);
    };
    window.addEventListener("ai-switch-category", handler);
    return () => window.removeEventListener("ai-switch-category", handler);
  }, []);

  return (
    <>
      <div id="category">
        <div id="categoryContainer">
          {categorys.map((category) => {
            const isActive = (!aiMode) && activeCategory === category;
            const cls = `categoryButton${isActive ? " active" : ""}`;
            return (
              <div
                key={category}
                className={cls}
                onClick={() => categoryHandle(category)}
                aria-pressed={isActive}
                aria-disabled="false"
                title={aiMode ? "AI 추천 상태에서 카테고리를 누르면 일반 목록으로 돌아갑니다." : undefined}
              >
                {category}
              </div>
            );
          })}
        </div>
      </div>

      <div id="mainBody">
        <div id="menuBox">
          {viewItems.map((item) => {
            const id = item.id;
            const name = item.이름;
            const price = Number(item.가격 || 0);
            const img = item.imageUrl || (name ? `/images/menu/${name}.png` : "/img/noimage.png");

            const onClick = async () => {
              if (id) return productHandle(id);
              if (name) return resolveAndOpenByName(name);
            };

            return (
              <div
                key={`${name}-${id ?? "ai"}`}
                className="menuCard"
                onClick={onClick}
              >
                <img src={img} alt={name || "추천 메뉴"} className="menuImg" />
                <div className="menuCardDetail">
                  <span className="menuName">{name || "추천 메뉴"}</span>
                  <span className="menuPrice">{price ? `${price.toLocaleString()}원` : ""}</span>
                </div>
              </div>
            );
          })}
        </div>

        {!aiMode && viewItems.length === 0 && (
          <div style={{ textAlign: "center", color: "#666", padding: 12 }}>
            메뉴를 불러오는 중입니다…
          </div>
        )}

        {aiMode && viewItems.length === 0 && (
          <div style={{ textAlign: "center", color: "#666", padding: 12 }}>
            추천 항목을 표시할 수 없습니다. 다시 시도해 주세요.
          </div>
        )}
      </div>
    </>
  );
}

export default MainBody;
