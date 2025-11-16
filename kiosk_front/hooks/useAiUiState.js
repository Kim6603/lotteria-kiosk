// src/hooks/useAiUiState.js
import { useEffect, useRef, useState } from "react";

const first = (...vals) => vals.find(v => v !== undefined && v !== null && v !== "") ?? undefined;

const looksLikeMenu = (o) => {
  if (!o || typeof o !== "object") return false;
  const name = first(o?.이름, o?.name, o?.menuName, o?.title);
  const id   = first(o?.id, o?.ID, o?.menuId, o?.menu_id, o?.Id);
  const price = first(o?.가격, o?.price, o?.cost, o?.amount);
  return Boolean(name || id) && (price === 0 || !!price || typeof price === "number");
};

const normalizeMenu = (o) => ({
  id: first(o?.id, o?.ID, o?.menuId, o?.menu_id, o?.Id),
  name: first(o?.이름, o?.name, o?.menuName, o?.title),
  price: Number(first(o?.가격, o?.price, o?.cost, o?.amount) ?? 0) || 0,
  category: first(o?.종류, o?.type, o?.category),
  imageUrl: o?.imageUrl,
  description: first(o?.설명, o?.description, o?.desc, o?.소개),
});

const parseFromText = (text) => {
  if (!text || typeof text !== "string") return null;
  const re = /(.+?)\s+([\d,]{3,9})\s*원(?=$|[\s.,!?:;)\]}])/;
  const m = re.exec(text);
  if (!m) return null;
  const name = (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim();
  const price = parseInt((m[2] || "0").replace(/[^\d]/g, ""), 10) || 0;
  if (!name) return null;
  return { name, price };
};

const pickMeta = (data) => {
  try {
    if (typeof data?.metaJson === "string" && data.metaJson.trim().startsWith("{")) {
      const obj = JSON.parse(data.metaJson);
      if (obj && typeof obj === "object") return obj;
    }
    const msg0 = Array.isArray(data?.messages) ? data.messages[0] : undefined;
    if (msg0 && typeof msg0?.metaJson === "string" && msg0.metaJson.trim().startsWith("{")) {
      const obj = JSON.parse(msg0.metaJson);
      if (obj && typeof obj === "object") return obj;
    }
    const meta = first(
      (Array.isArray(data?.messages) && typeof data.messages[0] === "object" && data.messages[0]?.meta) || null,
      data?.meta
    );
    if (meta && typeof meta === "object") return meta;
  } catch {}
  return null;
};

const normalizeCategory = (raw) => {
  if (!raw) return null;
  const s = String(raw).replace(/\s+/g, "").trim();
  const map = {
    "버거":"버거","디저트":"디저트","치킨":"치킨",
    "음료":"음료","음료수":"음료","아이스샷":"아이스샷","아이스":"아이스샷"
  };
  return map[s] || null;
};

const dispatchSwitchCategory = (cat) => {
  if (!cat) return;
  try { sessionStorage.setItem("aiSwitchCategory", cat); } catch {}
  window.dispatchEvent(new CustomEvent("ai-switch-category", { detail: { category: cat } }));
  console.log("[AI][Hook] dispatch ai-switch-category:", cat);
};

const readCart = () => {
  try { const c = JSON.parse(sessionStorage.getItem("cart") || "[]"); return Array.isArray(c) ? c : []; } catch { return []; }
};
const saveCart = (next) => {
  sessionStorage.setItem("cart", JSON.stringify(next));
  window.dispatchEvent(new CustomEvent("cart-updated", { detail: { cart: next } }));
};

const normLine = (x) => {
  if (!x || typeof x !== "object") return null;
  const id = first(x.id, x.menuId, x.menu_id, x.ID, x.Id);
  const name = first(x.이름, x.name, x.menuName, x.title);
  const price = Number(first(x.가격, x.price, x.cost, x.amount) ?? 0) || 0;
  const count = Math.max(1, Number(x.count ?? 1) || 1);
  const imageUrl = x.imageUrl;
  return { id, name, price, count, imageUrl };
};
const sameItem = (a, b) => {
  if (!a || !b) return false;
  if (a.id && b.id) return String(a.id) === String(b.id);
  if (a.name && b.name) return a.name.replace(/\s+/g, "") === b.name.replace(/\s+/g, "");
  return false;
};
const sameNameLoose = (aName, bName) => {
  const A = (aName || "").replace(/\s+/g, "").trim();
  const B = (bName || "").replace(/\s+/g, "").trim();
  if (!A || !B) return false;
  return A === B || A.includes(B) || B.includes(A);
};

const applyCartOps = (ops) => {
  if (!ops || typeof ops !== "object") return;
  let cart = readCart();
  const log = (...a) => console.log("[AI][Cart]", ...a);

  if (ops.clear) {
    cart = [];
    log("clear");
  }

  if (Array.isArray(ops.removeAll)) {
    for (const raw of ops.removeAll) {
      const one = normLine(raw);
      if (!one) continue;
      const before = cart.length;
      cart = cart.filter(ci => !sameItem(ci, one));
      if (before === cart.length) cart = cart.filter(ci => !sameNameLoose(ci.name, one.name));
      log("removeAll:", one.name || one.id, "removed:", before - cart.length);
    }
  }

  if (Array.isArray(ops.remove)) {
    for (const raw of ops.remove) {
      const one = normLine(raw);
      if (!one) continue;
      let hit = false;
      cart = cart.map(ci => {
        if (!hit && sameItem(ci, one)) {
          hit = true;
          const nextCnt = (Number(ci.count || 0) - (one.count || 1));
          log("remove:", one.name || one.id, "-", one.count, "=>", nextCnt);
          return { ...ci, count: nextCnt };
        }
        return ci;
      });
      if (!hit) {
        cart = cart.map(ci => {
          if (!hit && sameNameLoose(ci.name, one.name)) {
            hit = true;
            const nextCnt = (Number(ci.count || 0) - (one.count || 1));
            log("remove(loose):", one.name || one.id, "-", one.count, "=>", nextCnt);
            return { ...ci, count: nextCnt };
          }
          return ci;
        });
      }
      cart = cart.filter(ci => (ci.count || 0) > 0);
      if (!hit) log("remove: target not found:", one.name || one.id);
    }
  }

  if (Array.isArray(ops.add)) {
    for (const raw of ops.add) {
      const one = normLine(raw);
      if (!one) continue;
      let found = false;
      cart = cart.map(ci => {
        if (!found && sameItem(ci, one)) {
          found = true;
          const nextCnt = Number(ci.count || 0) + (one.count || 1);
          log("add: +", one.count, one.name || one.id, "=>", nextCnt);
          return { ...ci, count: nextCnt, price: one.price || ci.price, imageUrl: one.imageUrl || ci.imageUrl };
        }
        return ci;
      });
      if (!found) {
        cart.push({ name: one.name, price: one.price, count: one.count, id: one.id, imageUrl: one.imageUrl });
        log("add: new item:", one.name || one.id, "x", one.count);
      }
    }
  }
  saveCart(cart);
};

const normName = (s) => (s || "").replace(/\s+/g, "").trim();
const parseQtyWord = (s) => {
  if (!s) return null;
  const map = { "한":1,"하나":1,"한개":1,"1":1,"두":2,"둘":2,"두개":2,"2":2,"세":3,"셋":3,"세개":3,"3":3,"네":4,"넷":4,"네개":4,"4":4,
    "다섯":5,"5":5,"여섯":6,"6":6,"일곱":7,"7":7,"여덟":8,"8":8,"아홉":9,"9":9 };
  for (const k of Object.keys(map)) if (s.includes(k)) return map[k];
  const mm = s.match(/(\d{1,2})\s*(개|잔)?/);
  if (mm) return Math.max(1, Math.min(9, parseInt(mm[1], 10)));
  return null;
};
const parseCartActionFromAnswer = (answerText) => {
  if (typeof answerText !== "string" || !answerText.trim()) return null;
  const txt = answerText.replace(/\s+/g, " ").trim();
  if (/(장바구니|카트).*(비웠|비워|초기화|모두 비웠|모두비웠|싹 비웠)/.test(txt)) {
    return { type: "clear" };
  }
  let m = txt.match(/(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s+(?:전부|모두|다|전체)\s*[^\n]*?(?:장바구니|카트)[^\n]*?(?:빼|뺐|삭제|제거)/);
  if (m) return { type: "removeAll", name: (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim() };
  m = txt.match(/(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s+((?:\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉))\s*(?:개|잔)[^\n]*?(?:장바구니|카트)[^\n]*?(?:담았|넣었|추가)/);
  if (m) return { type: "add", name: (m[1]||"").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(), qty: parseQtyWord(m[2]) ?? 1 };
  m = txt.match(/(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s+((?:\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉))\s*(?:개|잔)?\s*[^\n]*?(?:장바구니|카트)[^\n]*?(?:빼|뺐|삭제|제거)/);
  if (m) return { type: "remove", name: (m[1]||"").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(), qty: parseQtyWord(m[2]) ?? 1 };
  m = txt.match(/(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s*(?:장바구니|카트)[^\n]*?(?:빼|뺐|삭제|제거)/);
  if (m) return { type: "remove", name: (m[1]||"").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(), qty: 1 };
  return null;
};
const applyCartTextAction = async (action) => {
  if (!action || !action.type) return;
  if (action.type === "clear") return saveCart([]);
  let cart = readCart();
  const idxExact = cart.findIndex(ci => normName(ci.name) === normName(action.name));
  const idxLoose = cart.findIndex(ci => sameNameLoose(ci.name, action.name));
  let idx = idxExact >= 0 ? idxExact : idxLoose;
  if (action.type === "removeAll") {
    if (idx >= 0) saveCart(cart.filter((_, i) => i !== idx));
    return;
    }
  if (action.type === "add") {
    if (idx >= 0) {
      const next = cart.map((ci,i)=> i===idx ? { ...ci, count: (Number(ci.count||0)+(action.qty||1)) } : ci);
      return saveCart(next);
    }
    const toAdd = { name: action.name, id: undefined, price: 0, count: action.qty||1, imageUrl: undefined };
    return saveCart([...cart, toAdd]);
  }
  if (action.type === "remove") {
    if (idx >= 0) {
      const nextCnt = (Number(cart[idx].count||0) - (action.qty||1));
      if (nextCnt <= 0) return saveCart(cart.filter((_,i)=> i!==idx));
      const next = cart.map((ci,i)=> i===idx ? { ...ci, count: nextCnt } : ci);
      return saveCart(next);
    }
  }
};

export const extractAudioRef = (data) => {
  const msg0 = Array.isArray(data?.messages) ? data.messages[0] : undefined;
  const base64 = (msg0 && typeof msg0 === "object" && (msg0.audio || msg0.audioBase64 || msg0.speechBase64))
    || data?.audio || data?.audioBase64 || data?.speechBase64 || null;
  if (base64) return { kind: "base64", value: base64 };
  const urlCand = first(msg0?.audioUrl, msg0?.voiceUrl, msg0?.ttsUrl, data?.audioUrl, data?.voiceUrl, data?.ttsUrl);
  if (typeof urlCand === "string" && urlCand.startsWith("data:audio")) return { kind: "dataurl", value: urlCand };

  const textCands = [];
  if (msg0 && typeof msg0 === "object") textCands.push(first(msg0.response, msg0.message, msg0.content, msg0.answer, msg0.text));
  textCands.push(first(data?.response, data?.message, data?.content, data?.answer, data?.text));
  for (const t of textCands) {
    if (typeof t === "string") {
      const m = t.match(/data:audio\/[a-zA-Z0-9+.-]+;base64,[A-Za-z0-9+/=]+/);
      if (m) return { kind: "dataurl", value: m[0] };
    }
  }
  return null;
};

export const getAnswerText = (data) => {
  const msg0 = Array.isArray(data?.messages) ? data.messages[0] : undefined;
  let answerText;
  if (typeof msg0 === "string") answerText = msg0;
  else if (msg0 && typeof msg0 === "object") {
    answerText = first(msg0.response, msg0.message, msg0.content, msg0.answer, msg0.text);
  }
  return first(answerText, data?.response, data?.message, data?.content, data?.answer, data?.text);
};

const extractRecommendationRobust = (data) => {
  try {
    const meta = pickMeta(data);
    if (meta) {
      if (Array.isArray(meta.recommendedMenus) && meta.recommendedMenus.length) {
        const cand = meta.recommendedMenus.find(looksLikeMenu) || meta.recommendedMenus[0];
        if (cand) return normalizeMenu(cand);
      }
      if (Array.isArray(meta.recommendedMenuIds) && meta.recommendedMenuIds.length) {
        return { id: meta.recommendedMenuIds[0] };
      }
      if (meta.menu && looksLikeMenu(meta.menu)) return normalizeMenu(meta.menu);
    }
    const msg0 = Array.isArray(data?.messages) ? data.messages[0] : undefined;
    let answerText;
    if (typeof msg0 === "string") answerText = msg0;
    else if (msg0 && typeof msg0 === "object") {
      answerText = first(msg0.response, msg0.message, msg0.content, msg0.answer, msg0.text);
    }
    answerText = first(answerText, data?.response, data?.message, data?.content, data?.answer, data?.text);
    const parsed = parseFromText(answerText);
    if (parsed) return parsed;

    const containers = [data?.recommendedMenus, data?.menus, data?.items, data?.results, data?.payload, data?.data].filter(Boolean);
    for (const c of containers) {
      if (Array.isArray(c) && c.length) {
        const obj = c.find(looksLikeMenu);
        if (obj) return normalizeMenu(obj);
      } else if (looksLikeMenu(c)) {
        return normalizeMenu(c);
      }
    }
    const stack = [data];
    const visited = new Set();
    while (stack.length) {
      const cur = stack.pop();
      if (!cur || typeof cur !== "object" || visited.has(cur)) continue;
      visited.add(cur);
      if (looksLikeMenu(cur)) return normalizeMenu(cur);
      if (Array.isArray(cur)) for (const v of cur) stack.push(v);
      else for (const k of Object.keys(cur)) stack.push(cur[k]);
    }
  } catch {}
  return null;
};

const maybeDispatchSwitchCategoryFromText = (answerText) => {
  if (typeof answerText !== "string" || !answerText.trim()) return;
  const txt = answerText.replace(/\s+/g, " ").trim();
  const tail = "(?:이동(?:하겠|합)?|가(?:겠|세|요)?)?";
  const rules = [
    { re: new RegExp(`(버거)\\s*(?:탭)?\\s*으로\\s*${tail}`, "i"), val: "버거" },
    { re: new RegExp(`(디저트)\\s*(?:탭)?\\s*으로\\s*${tail}`, "i"), val: "디저트" },
    { re: new RegExp(`(치킨)\\s*(?:탭)?\\s*으로\\s*${tail}`, "i"), val: "치킨" },
    { re: new RegExp(`(음료|음료수)\\s*(?:탭)?\\s*으로\\s*${tail}`, "i"), val: "음료" },
    { re: new RegExp(`(아이스\\s*샷)\\s*(?:탭)?\\s*으로\\s*${tail}`, "i"), val: "아이스샷" },
  ];
  for (const r of rules) {
    if (r.re.test(txt)) return dispatchSwitchCategory(r.val);
  }
  const m = txt.match(/([가-힣A-Za-z\s]+?)\s*탭\s*으로\s*(?:이동|이동하겠|이동합|가|가겠|가세|가요)/i);
  if (m) {
    const guess = normalizeCategory(m[1] || "");
    if (guess) return dispatchSwitchCategory(guess);
  }
};

export function useAiUiState() {
  const [aiMode, setAiMode] = useState(false);
  const [aiItems, setAiItems] = useState([]);
  const lastAnswerRef = useRef("");

  useEffect(() => {
    try {
      const lock = sessionStorage.getItem("aiUiLock") === "true";
      const items = JSON.parse(sessionStorage.getItem("aiRecommendation") || "[]");
      if (lock && Array.isArray(items) && items.length) {
        setAiItems(items);
        setAiMode(true);
        console.log("[AI][Hook] restored aiRecommendation:", items[0]);
      }
    } catch {}
  }, []);

  const clearAiLock = () => {
    setAiMode(false);
    setAiItems([]);
    sessionStorage.removeItem("aiUiLock");
    sessionStorage.removeItem("aiRecommendation");
  };

  const setRecommendation = (one) => {
    if (!one) return;
    const arr = [one];
    setAiItems(arr);
    setAiMode(true);
    try {
      sessionStorage.setItem("aiUiLock", "true");
      sessionStorage.setItem("aiRecommendation", JSON.stringify(arr));
    } catch {}
  };

  const handleAiResponse = async (data) => {
    const meta = pickMeta(data);
    const cand = meta?.uiOps?.switchCategory ?? meta?.switchCategory ?? meta?.category ?? null;
    const normalized = normalizeCategory(cand);
    if (normalized) dispatchSwitchCategory(normalized);
    else maybeDispatchSwitchCategoryFromText(getAnswerText(data));

    if (meta?.cartOps) {
      applyCartOps(meta.cartOps);
    } else {
      const ans = getAnswerText(data);
      const action = parseCartActionFromAnswer(ans);
      if (action) await applyCartTextAction(action);
    }

    const rec = extractRecommendationRobust(data);
    if (rec) setRecommendation(rec);
    else console.log("[AI][Hook] 추천 없음(유지)");
  };

  const handleAnswerTextOnly = async (text) => {
    if (typeof text !== "string" || !text.trim()) return;
    if (text === lastAnswerRef.current) return;
    lastAnswerRef.current = text;
    maybeDispatchSwitchCategoryFromText(text);
    const action = parseCartActionFromAnswer(text);
    if (action) await applyCartTextAction(action);
  };

  return {
    aiMode, aiItems,
    setRecommendation,
    clearAiLock,
    handleAiResponse,
    handleAnswerTextOnly,
  };
}
