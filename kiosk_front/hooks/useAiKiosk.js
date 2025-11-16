// src/hooks/useAiKiosk.jsx

import { useEffect, useRef, useState, useCallback } from "react";

/* ====== 유틸 함수들 ====== */
const first = (...vals) => vals.find(v => v !== undefined && v !== null && v !== "") ?? undefined;

const looksLikeMenu = (o) => {
  if (!o || typeof o !== "object") return false;
  const name = first(o?.이름, o?.name, o?.menuName, o?.title);
  const id = first(o?.id, o?.ID, o?.menuId, o?.menu_id, o?.Id);
  const price = first(o?.가격, o?.price, o?.cost, o?.amount);
  return Boolean(name || id) && (price === 0 || !!price || typeof price === "number");
};

const normalizeMenu = (o) => ({
  id: first(o?.id, o?.ID, o?.menuId, o?.menu_id, o?.Id),
  name: first(o?.이름, o?.name, o?.menuName, o?.title),
  price: Number(first(o?.가격, o?.price, o?.cost, o?.amount) ?? 0) || 0,
  category: first(o?.종류, o?.type, o?.category),
  description: first(o?.설명, o?.description, o?.desc, o?.소개),
  imageUrl: o?.imageUrl,
});

const parseFromText = (text) => {
  if (!text || typeof text !== "string") return null;
  const re = /(.+?)\s+([\d,]{3,9})\s*원(?=$|[\s.,!?:;)\]}])/;
  const m = re.exec(text);
  if (m) {
    const name = (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim();
    const price = parseInt((m[2] || "0").replace(/[^\d]/g, ""), 10) || 0;
    if (name) return { name, price };
  }
  return null;
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

function extractRecommendationRobust(data) {
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

    const containers = [
      data?.recommendedMenus, data?.menus, data?.items,
      data?.results, data?.payload, data?.data,
    ].filter(Boolean);
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
}

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

const readCart = () => {
  try {
    const c = JSON.parse(sessionStorage.getItem("cart") || "[]");
    return Array.isArray(c) ? c : [];
  } catch {
    return [];
  }
};

const saveCart = (next) => {
  sessionStorage.setItem("cart", JSON.stringify(next));
  window.dispatchEvent(new CustomEvent("cart-updated", { detail: { cart: next } }));
};

const extractCartOps = (data) => {
  const meta = pickMeta(data);
  if (!meta || typeof meta !== "object") return null;
  const ops = meta.cartOps;
  if (!ops || typeof ops !== "object") return null;
  return ops;
};

function applyCartOps(ops) {
  let cart = readCart();
  const log = (...a) => console.log("[AI][Cart]", ...a);

  if (ops?.clear) {
    cart = [];
    log("clear");
  }

  if (Array.isArray(ops?.removeAll)) {
    for (const raw of ops.removeAll) {
      const one = normLine(raw);
      if (!one) continue;
      const before = cart.length;
      cart = cart.filter(ci => !sameItem(ci, one));
      if (before === cart.length) cart = cart.filter(ci => !sameNameLoose(ci.name, one.name));
      log("removeAll:", one.name || one.id, "removed:", before - cart.length);
    }
  }

  if (Array.isArray(ops?.remove)) {
    for (const raw of ops.remove) {
      const one = normLine(raw);
      if (!one) continue;
      let hit = false;
      cart = cart.map(ci => {
        if (!hit && sameItem(ci, one)) {
          hit = true;
          const nextCnt = (Number(ci.count || 0) - (one.count || 1));
          return { ...ci, count: nextCnt };
        }
        return ci;
      });
      if (!hit) {
        cart = cart.map(ci => {
          if (!hit && sameNameLoose(ci.name, one.name)) {
            hit = true;
            const nextCnt = (Number(ci.count || 0) - (one.count || 1));
            return { ...ci, count: nextCnt };
          }
          return ci;
        });
      }
      cart = cart.filter(ci => (ci.count || 0) > 0);
      if (!hit) log("remove: target not found:", one.name || one.id);
    }
  }

  if (Array.isArray(ops?.add)) {
    for (const raw of ops.add) {
      const one = normLine(raw);
      if (!one) continue;
      let found = false;
      cart = cart.map(ci => {
        if (!found && sameItem(ci, one)) {
          found = true;
          const nextCnt = Number(ci.count || 0) + (one.count || 1);
          return {
            ...ci,
            count: nextCnt,
            price: one.price || ci.price,
            imageUrl: one.imageUrl || ci.imageUrl,
          };
        }
        return ci;
      });
      if (!found)
        cart.push({
          name: one.name,
          price: one.price,
          count: one.count,
          id: one.id,
          imageUrl: one.imageUrl,
        });
    }
  }

  saveCart(cart);
}

/* ==== 자연어 장바구니 파싱 ==== */
const parseQtyWord = (s) => {
  if (!s) return null;
  const map = {
    "한": 1, "하나": 1, "한개": 1, "1": 1,
    "두": 2, "둘": 2, "두개": 2, "2": 2,
    "세": 3, "셋": 3, "세개": 3, "3": 3,
    "네": 4, "넷": 4, "네개": 4, "4": 4,
    "다섯": 5, "5": 5,
    "여섯": 6, "6": 6,
    "일곱": 7, "7": 7,
    "여덟": 8, "8": 8,
    "아홉": 9, "9": 9,
  };
  for (const k of Object.keys(map)) if (s.includes(k)) return map[k];
  const mm = s.match(/(\d{1,2})\s*(개|잔)?/);
  if (mm) return Math.max(1, Math.min(9, parseInt(mm[1], 10)));
  return null;
};

function parseCartActionFromAnswer(answerText) {
  if (typeof answerText !== "string" || !answerText.trim()) return null;
  const txt = answerText.replace(/\s+/g, " ").trim();

  if (/(장바구니|카트).*(비웠|비워|초기화|모두 비웠|모두비웠|싹 비웠)/.test(txt))
    return { type: "clear" };

  let m = txt.match(
    /(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s+(?:전부|모두|다|전체)\s*[^\n]*?(?:장바구니|카트)[^\n]*?(?:빼|뺐|삭제|제거)/
  );
  if (m)
    return {
      type: "removeAll",
      name: (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(),
    };

  m = txt.match(
    /(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s+((?:\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉))\s*(?:개|잔)[^\n]*?(?:장바구니|카트)[^\n]*?(?:담았|넣었|추가)/
  );
  if (m)
    return {
      type: "add",
      name: (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(),
      qty: parseQtyWord(m[2]) ?? 1,
    };

  m = txt.match(
    /(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s+((?:\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉))\s*(?:개|잔)?\s*[^\n]*?(?:장바구니|카트)[^\n]*?(?:빼|뺐|삭제|제거)/
  );
  if (m)
    return {
      type: "remove",
      name: (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(),
      qty: parseQtyWord(m[2]) ?? 1,
    };

  m = txt.match(
    /(?:"|“|‘)?\s*(.+?)\s*(?:"|”|’)?\s*(?:장바구니|카트)[^\n]*?(?:빼|뺐|삭제|제거)/
  );
  if (m)
    return {
      type: "remove",
      name: (m[1] || "").replace(/^[\s"“'‘]+|[\s"”'’]+$/g, "").trim(),
      qty: 1,
    };

  return null;
}

const normName = (s) => (s || "").replace(/\s+/g, "").trim();

async function resolveByNameFromSessionOrApi(name) {
  try {
    const pools = [
      JSON.parse(sessionStorage.getItem("aiRecommendation") || "[]"),
      [JSON.parse(sessionStorage.getItem("lastAiParsed") || "null")].filter(Boolean),
      [JSON.parse(sessionStorage.getItem("lastViewItems0@MainBody") || "null")].filter(Boolean),
    ];
    for (const arr of pools) {
      if (!Array.isArray(arr)) continue;
      for (const it of arr) {
        const nm = it?.이름 ?? it?.name ?? it?.menuName ?? it?.title;
        const id = it?.id ?? it?.menuId ?? it?.menu_id ?? it?.ID ?? it?.Id;
        const price = Number(it?.가격 ?? it?.price ?? it?.cost ?? it?.amount ?? 0) || 0;
        const imageUrl = it?.imageUrl;
        if (normName(nm) === normName(name)) return { id, name: nm, price, imageUrl };
      }
    }
  } catch {}
  const categories = ["버거", "디저트", "치킨", "음료", "아이스샷"];
  for (const cat of categories) {
    try {
      const res = await fetch("/api/type", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ 종류: cat }),
      });
      const list = await res.json();
      for (const it of list || []) {
        const nm = it?.이름 ?? it?.name ?? it?.menuName ?? it?.title;
        if (normName(nm) === normName(name)) {
          const id = it?.id ?? it?.menuId ?? it?.menu_id ?? it?.ID ?? it?.Id;
          const price = Number(it?.가격 ?? it?.price ?? it?.cost ?? it?.amount ?? 0) || 0;
          const imageUrl = it?.imageUrl;
          return { id, name: nm, price, imageUrl };
        }
      }
    } catch {}
  }
  return { id: undefined, name, price: 0, imageUrl: undefined };
}

function extractAudioRef(data) {
  const msg0 = Array.isArray(data?.messages) ? data.messages[0] : undefined;
  const base64 =
    (msg0 && typeof msg0 === "object" && (msg0.audio || msg0.audioBase64 || msg0.speechBase64)) ||
    data?.audio || data?.audioBase64 || data?.speechBase64 || null;
  if (base64) return { kind: "base64", value: base64 };

  const urlCand = first(
    msg0?.audioUrl, msg0?.voiceUrl, msg0?.ttsUrl,
    data?.audioUrl, data?.voiceUrl, data?.ttsUrl
  );
  if (typeof urlCand === "string" && urlCand.startsWith("data:audio")) return { kind: "dataurl", value: urlCand };

  const textCands = [];
  if (msg0 && typeof msg0 === "object")
    textCands.push(first(msg0.response, msg0.message, msg0.content, msg0.answer, msg0.text));
  textCands.push(first(data?.response, data?.message, data?.content, data?.answer, data?.text));
  for (const t of textCands) {
    if (typeof t === "string") {
      const m = t.match(/data:audio\/[a-zA-Z0-9+.-]+;base64,[A-Za-z0-9+/=]+/);
      if (m) return { kind: "dataurl", value: m[0] };
    }
  }
  return null;
}

const getAnswerText = (data) => {
  const msg0 = Array.isArray(data?.messages) ? data.messages[0] : undefined;
  let answerText;
  if (typeof msg0 === "string") answerText = msg0;
  else if (msg0 && typeof msg0 === "object")
    answerText = first(msg0.response, msg0.message, msg0.content, msg0.answer, msg0.text);
  return first(answerText, data?.response, data?.message, data?.content, data?.answer, data?.text);
};

const normalizeCategory = (raw) => {
  if (!raw) return null;
  const s = String(raw).replace(/\s+/g, "").trim();
  const map = {
    "버거": "버거",
    "디저트": "디저트",
    "치킨": "치킨",
    "음료": "음료",
    "음료수": "음료",
    "아이스샷": "아이스샷",
    "아이스": "아이스샷",
  };
  return map[s] || null;
};

const extractSwitchCategory = (data) => {
  const meta = pickMeta(data);
  if (!meta || typeof meta !== "object") return null;
  const cand = meta?.uiOps?.switchCategory ?? meta?.switchCategory ?? meta?.category ?? null;
  return normalizeCategory(cand);
};

function maybeDispatchSwitchCategoryFromAnswerText(answerText) {
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
    if (r.re.test(txt)) {
      const cat = r.val;
      try {
        sessionStorage.setItem("aiSwitchCategory", cat);
      } catch {}
      window.dispatchEvent(new CustomEvent("ai-switch-category", { detail: { category: cat } }));
      console.log("[AI] dispatch ai-switch-category (text):", cat);
      return;
    }
  }
}

async function requestTts(text) {
  if (!text || !text.trim()) {
    return { audioBase64: null, audioDataUrl: null, ttsMillis: 0 };
  }
  try {
    const res = await fetch("/api/tts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text }),
    });
    if (!res.ok) {
      const t = await res.text().catch(() => "");
      throw new Error(`HTTP ${res.status}: ${t}`);
    }
    const data = await res.json();
    return data;
  } catch (e) {
    console.warn("[useAiKiosk] TTS request failed:", e.message);
    return { audioBase64: null, audioDataUrl: null, ttsMillis: 0 };
  }
}

export function useAiKiosk({ onAiRecommend } = {}) {
  const [isRecording, setIsRecording] = useState(false);
  const [isSending, setIsSending] = useState(false);

  const recognitionRef = useRef(null);
  const audioRef = useRef(null);
  const audioPlayingRef = useRef(false);
  const loopEnabledRef = useRef(false);
  const finalizeOnceRef = useRef(false);

  const sttStartRef = useRef(null);

  const restartMicIfNeeded = useCallback(() => {
    if (finalizeOnceRef.current) return;
    if (!loopEnabledRef.current) return;
    const rec = recognitionRef.current;
    if (!rec) return;
    if (audioPlayingRef.current) return;
    if (isSending) return;
    try {
      rec.start();
      setIsRecording(true);
      console.log("[AI][Voice] Auto restart microphone");
    } catch (err) {
      console.debug("[AI][Voice] restart start() ignored:", err?.message || err);
    }
  }, [isSending]);

  useEffect(() => {
    const Rec = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Rec) return;

    const rec = new Rec();
    rec.lang = "ko-KR";
    rec.interimResults = false;
    rec.continuous = false;

    rec.onspeechstart = () => {
      const now = typeof performance !== "undefined" ? performance.now() : Date.now();
      sttStartRef.current = now;
      console.log("[AI][Voice][STT] speech start at", now);
    };

    rec.onspeechend = () => {
      const now = typeof performance !== "undefined" ? performance.now() : Date.now();
      console.log("[AI][Voice][STT] speech end at", now);
    };

    rec.onresult = async (e) => {
      const now = typeof performance !== "undefined" ? performance.now() : Date.now();
      let sttMillis = null;
      if (sttStartRef.current != null) {
        sttMillis = Math.max(0, Math.round(now - sttStartRef.current));
      } else {
        sttMillis = 0;
      }

      console.log("[AI][Voice][STT] duration(ms) =", sttMillis);

      const transcript = Array.from(e.results)
        .map(r => r[0].transcript)
        .join(" ")
        .trim();

      setIsRecording(false);
      sttStartRef.current = null;

      if (transcript) {
        await sendToAi(transcript, sttMillis);
      }
    };

    rec.onend = () => {
      if (!finalizeOnceRef.current) setTimeout(() => restartMicIfNeeded(), 120);
    };

    rec.onerror = () => {
      setTimeout(() => restartMicIfNeeded(), 250);
    };

    recognitionRef.current = rec;
  }, [restartMicIfNeeded]);

  const playAudioIfAny = useCallback(async (data) => {
    try {
      let audioRefObj = extractAudioRef(data);

      if (!audioRefObj) {
        const answerText = getAnswerText(data);
        if (!answerText || !answerText.trim()) {
          setTimeout(() => restartMicIfNeeded(), 0);
          return;
        }

        const ttsRes = await requestTts(answerText);
        if (ttsRes && (ttsRes.audioDataUrl || ttsRes.audioBase64)) {
          const value = ttsRes.audioDataUrl
            ? ttsRes.audioDataUrl
            : `data:audio/mp3;base64,${ttsRes.audioBase64}`;
          audioRefObj = { kind: "dataurl", value };
        }
      }

      if (!audioRefObj) {
        setTimeout(() => restartMicIfNeeded(), 0);
        return;
      }

      if (audioRef.current) {
        try {
          audioRef.current.pause();
        } catch {}
      }

      const src =
        audioRefObj.kind === "base64"
          ? `data:audio/mp3;base64,${audioRefObj.value}`
          : audioRefObj.value;

      const audio = new Audio(src);
      audioRef.current = audio;
      audioPlayingRef.current = true;

      audio.onended = () => {
        audioPlayingRef.current = false;
        restartMicIfNeeded();
      };
      audio.onerror = () => {
        audioPlayingRef.current = false;
        restartMicIfNeeded();
      };

      await audio.play();
    } catch (e) {
      console.warn("[useAiKiosk] playAudioIfAny error:", e);
      audioPlayingRef.current = false;
      restartMicIfNeeded();
    }
  }, [restartMicIfNeeded]);

  const sendToAi = useCallback(
    async (text, sttMillis) => {
      if (isSending) return;
      setIsSending(true);
      try {
        const sessionId = sessionStorage.getItem("chatSessionId") || "";
        const body = {
          message: text,
          sessionId,
        };
        if (typeof sttMillis === "number" && !Number.isNaN(sttMillis)) {
          body.sttMillis = sttMillis;
        }

        const res = await fetch("/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        const data = await res.json();
        try {
          sessionStorage.setItem("lastAiRaw", JSON.stringify(data));
        } catch {}

        if (data?.sessionId) sessionStorage.setItem("chatSessionId", data.sessionId);

        await playAudioIfAny(data);

        let switchCat = extractSwitchCategory(data);
        if (switchCat) {
          try {
            sessionStorage.setItem("aiSwitchCategory", switchCat);
          } catch {}
          window.dispatchEvent(
            new CustomEvent("ai-switch-category", { detail: { category: switchCat } })
          );
          console.log("[AI] dispatch ai-switch-category:", switchCat);
        } else {
          const answerText = getAnswerText(data);
          maybeDispatchSwitchCategoryFromAnswerText(answerText);
        }

        const cartOps = extractCartOps(data);
        if (cartOps) {
          applyCartOps(cartOps);
        } else {
          const answerText = getAnswerText(data);
          const action = parseCartActionFromAnswer(answerText);
          if (action) {
            if (action.type === "clear") {
              saveCart([]);
            } else if (action.type === "removeAll") {
              let cart = readCart().filter(ci => !sameNameLoose(ci.name, action.name));
              saveCart(cart);
            } else if (action.type === "add") {
              let cart = readCart();
              let idx = cart.findIndex(ci => normName(ci.name) === normName(action.name));
              if (idx >= 0) {
                cart[idx] = {
                  ...cart[idx],
                  count: (Number(cart[idx].count || 0) + action.qty),
                };
                saveCart(cart);
              } else {
                const resolved = await resolveByNameFromSessionOrApi(action.name);
                cart.push({
                  name: resolved.name || action.name,
                  id: resolved.id,
                  price: Number(resolved.price || 0),
                  count: action.qty,
                  imageUrl: resolved.imageUrl,
                });
                saveCart(cart);
              }
            } else if (action.type === "remove") {
              let cart = readCart();
              let idx = cart.findIndex(ci => normName(ci.name) === normName(action.name));
              if (idx >= 0) {
                const nextCnt = (Number(cart[idx].count || 0) - (action.qty || 1));
                if (nextCnt <= 0) cart = cart.filter((_, i) => i !== idx);
                else cart[idx] = { ...cart[idx], count: nextCnt };
                saveCart(cart);
              }
            }
          }
        }

        const rec = extractRecommendationRobust(data);
        if (rec) {
          const items = [rec];
          try {
            sessionStorage.setItem("aiRecommendation", JSON.stringify(items));
          } catch {}
          if (typeof onAiRecommend === "function") onAiRecommend(items, true);
          window.dispatchEvent(
            new CustomEvent("ai-recommend", {
              detail: { items, lock: true },
            })
          );
        }
      } catch {
      } finally {
        setIsSending(false);
      }
    },
    [isSending, onAiRecommend, playAudioIfAny]
  );

  const toggleRecording = useCallback(() => {
    const rec = recognitionRef.current;
    if (!rec) {
      alert("음성 인식을 지원하지 않는 환경입니다.");
      return;
    }

    if (!loopEnabledRef.current) {
      loopEnabledRef.current = true;
      finalizeOnceRef.current = false;
      try {
        rec.start();
        setIsRecording(true);
        console.log("[AI][Voice] Mic loop enabled");
      } catch (err) {
        loopEnabledRef.current = false;
        sttStartRef.current = null;
        setIsRecording(false);
      }
      return;
    }

    loopEnabledRef.current = false;
    try {
      rec.stop();
    } catch {}
    if (audioRef.current) {
      try {
        audioRef.current.pause();
      } catch {}
      audioRef.current = null;
    }
    audioPlayingRef.current = false;
    sttStartRef.current = null;
    setIsRecording(false);
    console.log("[AI][Voice] Mic loop disabled");
  }, []);

  const hardStopAll = useCallback(() => {
    const rec = recognitionRef.current;
    finalizeOnceRef.current = true;
    loopEnabledRef.current = false;
    try {
      rec?.stop();
    } catch {}
    if (audioRef.current) {
      try {
        audioRef.current.pause();
      } catch {}
      audioRef.current = null;
    }
    audioPlayingRef.current = false;
    sttStartRef.current = null;
    setIsRecording(false);
  }, []);

  return {
    isRecording,
    isSending,
    toggleRecording,
    hardStopAll,
    sendToAi,
  };
}
