import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";

async function jsonFetch(url, options = {}) {
  const res = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`HTTP ${res.status}: ${text}`);
  }

  return res.json();
}

function useChatInternal() {
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);

  const sessionIdRef = useRef(null);

  const abortRef = useRef(null);

  const sendChatRequest = useCallback(async (payload) => {
    return jsonFetch("/api/chat", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  }, []);

  const requestTts = useCallback(async (text) => {
    if (!text || !text.trim()) {
      return { audioBase64: null, audioDataUrl: null, ttsMillis: 0 };
    }

    const body = { text };
    try {
      const data = await jsonFetch("/api/tts", {
        method: "POST",
        body: JSON.stringify(body),
      });
      return data;
    } catch (e) {
      console.warn("[useChat] TTS request failed:", e.message);
      return { audioBase64: null, audioDataUrl: null, ttsMillis: 0 };
    }
  }, []);

  const chat = useCallback(
    async (arg) => {
      if (abortRef.current) {
        abortRef.current.aborted = true;
      }
      const abortToken = { aborted: false };
      abortRef.current = abortToken;

      let text = "";
      let sttMillis = undefined;
      let clientPointer = undefined;
      let extra = {};

      if (typeof arg === "string") {
        text = arg;
      } else if (arg && typeof arg === "object") {
        text =
          arg.text ??
          arg.content ??
          arg.userText ??
          arg.message ??
          "";
        sttMillis = arg.sttMillis ?? arg.sttMs;
        clientPointer = arg.clientPointer ?? arg.pointer ?? arg.clientLast;
        extra = arg.extra ?? {};
      }

      text = (text ?? "").toString();
      if (!text.trim()) {
        return;
      }

      setLoading(true);

      if (!sessionIdRef.current) {
        sessionIdRef.current = crypto.randomUUID
          ? crypto.randomUUID()
          : String(Date.now()) + "-" + Math.random().toString(16).slice(2);
      }

      const chatPayload = {
        sessionId: sessionIdRef.current,
        sttMillis: sttMillis ?? null,
        clientPointer: clientPointer ?? null,
        messages: [
          {
            role: "user",
            content: text,
          },
        ],
        ...extra,
      };

      try {
        const chatRes = await sendChatRequest(chatPayload);
        if (abortToken.aborted) return;

        const messagesArr = chatRes.messages || [];
        const lastMsg =
          messagesArr[messagesArr.length - 1] ||
          messagesArr[0] ||
          chatRes;

        const answerText =
          lastMsg.content ??
          lastMsg.answer ??
          chatRes.answer ??
          "";

        const meta =
          lastMsg.meta ??
          chatRes.meta ??
          chatRes.responseMeta ??
          null;

        let audioUrl =
          lastMsg.audioUrl ||
          lastMsg.audioDataUrl ||
          chatRes.audioUrl ||
          chatRes.audioDataUrl ||
          null;
        let audioBase64 =
          lastMsg.audioBase64 ||
          chatRes.audioBase64 ||
          null;

        if (chatRes.sessionId && typeof chatRes.sessionId === "string") {
          sessionIdRef.current = chatRes.sessionId;
        }

        const baseMessage = {
          ...lastMsg,
          content: answerText,
          audioUrl,
          audioDataUrl: audioUrl,
          audioBase64,
          meta,
          raw: chatRes,
          played: false,
        };

        setMessage(baseMessage);
        setLoading(false);

        if (!audioUrl && !audioBase64 && answerText) {
          (async () => {
            try {
              const ttsRes = await requestTts(answerText);
              if (abortToken.aborted || !ttsRes) return;

              const nextAudioUrl = ttsRes.audioDataUrl || null;
              const nextAudioBase64 = ttsRes.audioBase64 || null;

              if (!nextAudioUrl && !nextAudioBase64) return;

              setMessage((prev) => {
                if (!prev) return prev;
                if (prev.content !== answerText) return prev;

                return {
                  ...prev,
                  audioUrl: nextAudioUrl || prev.audioUrl,
                  audioDataUrl: nextAudioUrl || prev.audioDataUrl,
                  audioBase64: nextAudioBase64 || prev.audioBase64,
                  ttsMillis:
                    typeof ttsRes.ttsMillis === "number"
                      ? ttsRes.ttsMillis
                      : prev.ttsMillis,
                };
              });
            } catch (e) {
              console.warn("[useChat] async TTS error:", e);
            }
          })();
        }
      } catch (e) {
        console.error("[useChat] chat error:", e);
        if (!abortToken.aborted) {
          setLoading(false);
        }
      }
    },
    [requestTts, sendChatRequest]
  );

  const onMessagePlayed = useCallback(() => {
    setMessage((prev) =>
      prev ? { ...prev, played: true } : prev
    );
  }, []);

  useEffect(() => {
    return () => {
      if (abortRef.current) {
        abortRef.current.aborted = true;
      }
    };
  }, []);

  return {
    chat,
    loading,
    message,
    onMessagePlayed,
  };
}

const ChatContext = createContext(null);

export function ChatProvider({ children }) {
  const value = useChatInternal();
  return (
    <ChatContext.Provider value={value}>
      {children}
    </ChatContext.Provider>
  );
}

export function useChat() {
  const ctx = useContext(ChatContext);
  if (!ctx) {
    throw new Error("useChat must be used within a ChatProvider");
  }
  return ctx;
}
