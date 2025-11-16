import { useEffect, useState } from "react";
import "../src/assets/styles/Ai.css";

/** 항상 text로 받아 안전 파싱 */
async function safeJsonFetch(url, init) {
  const res = await fetch(url, init);
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch {}
  if (!res.ok) {
    const msg = (data && (data.message || data.error)) || text || `HTTP ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

/** SSE(POST) 수신: fetch + ReadableStream 직접 파싱 */
async function* ssePost(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok || !res.body) {
    const msg = await res.text();
    throw new Error(msg || `HTTP ${res.status}`);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let idx;
    while ((idx = buffer.indexOf("\n\n")) !== -1) {
      const raw = buffer.slice(0, idx).trim(); // event: X\ndata: Y
      buffer = buffer.slice(idx + 2);
      if (!raw) continue;

      let event = "message";
      let data = "";
      for (const line of raw.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      yield { event, data };
    }
  }
}

function AiBody() {
  const [chatId, setChatId] = useState(null);
  const [inputMessage, setInputMessage] = useState("");
  const [assistantText, setAssistantText] = useState(""); // 지피티 멘트만 화면에
  const [combo, setCombo] = useState(null);               // { items:[...], totalPrice }
  const [error, setError] = useState("");

  // 세션 시작
  useEffect(() => {
    (async () => {
      try {
        const data = await safeJsonFetch("/api/recommend/session", { method: "POST" });
        setChatId(data.chatId);
      } catch (e) {
        setError(e.message);
      }
    })();
  }, []);

  const send = async () => {
    const q = inputMessage.trim();
    if (!q || !chatId) return;

    setAssistantText("");
    setCombo(null);
    setInputMessage("");
    setError("");

    try {
      for await (const { event, data } of ssePost("/api/recommend/stream", { chatId, query: q })) {
        if (event === "delta") setAssistantText((prev) => prev + data);
        else if (event === "combo") {
          try { setCombo(JSON.parse(data)?.[0] || null); } catch { /* ignore */ }
        }
      }
    } catch (e) {
      setError(e.message || "요청 처리 중 오류");
    }
  };

  return (
    <div id="aiBody">
      <div id="resultBox">
        {assistantText ? (
          <div className="aiMessage">{assistantText}</div>
        ) : (
          <div className="placeholder">이곳에 결과가 표시됩니다.</div>
        )}

        {combo && (
          <div className="comboBox">
            <div className="itemsBox">
              {combo.items.map((item) => (
                <div key={item.id} className="menuItem">
                  <img
                    src={`/images/menu/${item.name}.png`}
                    alt={item.name}
                    className="menuImg"
                    onError={(e) => (e.currentTarget.src = "/images/menu/default.png")}
                  />
                  <div className="menuLine">{item.name} - {item.price}원</div>
                </div>
              ))}
            </div>
            <div className="totalPrice">총 가격: {combo.totalPrice}원</div>
          </div>
        )}

        {error && <div className="errorBox">{error}</div>}
      </div>

      <div id="inputZone">
        <div id="inputBox">
          <input
            type="text"
            id="inputMessage"
            value={inputMessage}
            onChange={(e) => setInputMessage(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            placeholder="AI에게 메뉴 추천을 받아보세요."
          />
          <button onClick={send}>전송</button>
        </div>
      </div>
    </div>
  );
}

export default AiBody;
