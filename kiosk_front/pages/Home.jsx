import { useEffect, useRef, useState } from "react";
import { useChat } from "../hooks/useChat";
import { useAiUiState } from "../hooks/useAiUiState";
import Header from "../components/Header";
import MainBody from "../components/MainBody";
import Shop from "../components/Shop";
import Bottom from "../components/Bottom";

const first = (...vals) =>
  vals.find((v) => v !== undefined && v !== null && v !== "") ?? undefined;

function Home() {
  const { chat, loading, message, onMessagePlayed } = useChat();

  const {
    aiMode,
    aiItems,
    setRecommendation,
    clearAiLock,
    handleAiResponse,
    handleAnswerTextOnly,
  } = useAiUiState();

  const [isConversationMode, setIsConversationMode] = useState(false);
  const [isListening, setIsListening] = useState(false);

  const currentAudioRef = useRef(null);
  const chatRef = useRef(chat);
  useEffect(() => {
    chatRef.current = chat;
  }, [chat]);

  useEffect(() => {
    const safeParse = (v) => {
      try {
        const arr = JSON.parse(v ?? "[]");
        return Array.isArray(arr) ? arr : [];
      } catch {
        return [];
      }
    };
    const qtyOf = (it) => Number(it?.count ?? it?.qty ?? 1) || 1;
    const keyOf = (it) => {
      const id = it?.id ?? it?.menuId ?? it?.menu_id ?? it?.ID ?? it?.Id ?? null;
      const name = (it?.name ?? it?.이름 ?? "").replace(/\s+/g, "");
      return id ? `#${id}` : `@${name}`;
    };

    const diffAdds = (prevArr, nextArr) => {
      const prevMap = new Map();
      for (const p of prevArr)
        prevMap.set(keyOf(p), (prevMap.get(keyOf(p)) || 0) + qtyOf(p));
      const result = [];
      for (const n of nextArr) {
        const k = keyOf(n);
        const before = prevMap.get(k) || 0;
        const after = before < qtyOf(n) ? qtyOf(n) - before : 0;
        if (after > 0) result.push({ item: n, added: after });
      }
      return result;
    };

    const original = sessionStorage.setItem.bind(sessionStorage);
    sessionStorage.setItem = (key, value) => {
      if (key === "cart") {
        try {
          const prev = safeParse(sessionStorage.getItem("cart"));
          const next = safeParse(value);
          const adds = diffAdds(prev, next);
          for (const { item, added } of adds) {
            const nm = item?.name ?? item?.이름 ?? "(이름없음)";
            const id =
              item?.id ??
              item?.menuId ??
              item?.menu_id ??
              item?.ID ??
              item?.Id ??
              null;
            const price = Number(item?.price ?? item?.가격 ?? 0) || 0;
            if (added > 0) {
              console.info(
                "[CART][ADD]",
                `${nm}${id ? ` (#${id})` : ""} x ${added} → 단가 ${price.toLocaleString()}원`
              );
            }
          }
        } catch (e) {
          console.warn("[CART][ADD][ERROR] 로그 계산 실패:", e);
        }
      }
      return original(key, value);
    };

    return () => {
      sessionStorage.setItem = original;
    };
  }, []);

  useEffect(() => {
    if (!message) return;
    if (typeof message === "object") {
      handleAiResponse(message);
    } else if (typeof message === "string") {
      handleAnswerTextOnly(message);
    }
  }, [message, handleAiResponse, handleAnswerTextOnly]);

  useEffect(() => {
    if (!message?.audio) return;
    if (currentAudioRef.current) {
      try {
        currentAudioRef.current.pause();
      } catch {}
      currentAudioRef.current = null;
    }
    const audio = new Audio("data:audio/mp3;base64," + message.audio);
    currentAudioRef.current = audio;
    audio.play();
    audio.onended = () => onMessagePlayed();
    audio.onerror = () => onMessagePlayed();
  }, [message, onMessagePlayed]);

  useEffect(() => {
    const handler = (e) => {
      const { items = [], lock = true } = e?.detail || {};
      if (lock && Array.isArray(items) && items.length) {
        setRecommendation(items[0]);
      } else {
        clearAiLock();
      }
    };
    window.addEventListener("ai-recommend", handler);
    return () => window.removeEventListener("ai-recommend", handler);
  }, [setRecommendation, clearAiLock]);

  const toggleConversationMode = () =>
    setIsConversationMode((prev) => !prev);

  return (
    <div id="homeBody">
      <div id="homeRoot">
        <Header />

        <MainBody
          aiMode={aiMode}
          aiItems={aiItems}
          onClearAiLock={clearAiLock}
        />

        <Shop />

        <Bottom
          onAiRecommend={(items, lock = true) => {
            if (lock && Array.isArray(items) && items.length) {
              setRecommendation(items[0]);
              try {
                sessionStorage.setItem(
                  "aiRecommendation",
                  JSON.stringify([items[0]])
                );
                sessionStorage.setItem("aiUiLock", "true");
              } catch {}
            } else {
              clearAiLock();
            }
          }}
          toggleConversationMode={toggleConversationMode}
          isConversationMode={isConversationMode}
          isListening={isListening}
        />
      </div>
    </div>
  );
}

export default Home;
