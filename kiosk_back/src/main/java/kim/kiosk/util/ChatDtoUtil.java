package kim.kiosk.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import kim.kiosk.dto.ChatRequest;
import kim.kiosk.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ChatDtoUtil {

    private final ObjectMapper objectMapper;

    public String extractUserText(ChatRequest req) {
        if (req == null) return null;
        String[] tryNames = {"getMessage", "getContent", "getPrompt", "getInput",
                "message", "content", "prompt", "input"};
        for (String n : tryNames) {
            try {
                Method m = req.getClass().getMethod(n);
                Object v = m.invoke(req);
                if (v != null) return String.valueOf(v).trim();
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ex) { }
        }
        return req.toString();
    }

    public String extractSessionId(ChatRequest req) {
        if (req == null) return null;
        for (String n : new String[]{"getSessionId", "sessionId"}) {
            try {
                Method m = req.getClass().getMethod(n);
                Object v = m.invoke(req);
                if (v != null) return String.valueOf(v).trim();
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ex) { }
        }
        return null;
    }

    public String readString(Object obj, String... names) {
        if (obj == null) return null;
        if (obj instanceof Map<?,?> map) {
            for (String n : names) {
                Object v = map.get(n);
                if (v != null) return String.valueOf(v);
            }
        }
        Class<?> c = obj.getClass();
        for (String n : names) {
            for (String cand : new String[]{"get" + n, "get" + capitalize(n)}) {
                try {
                    Method m = c.getMethod(cand);
                    Object v = m.invoke(obj);
                    if (v != null) return String.valueOf(v);
                } catch (NoSuchMethodException ignore) {
                } catch (Exception ignore) { }
            }
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return String.valueOf(v);
            } catch (NoSuchFieldException ignore) {
            } catch (Exception ignore) { }
        }
        return null;
    }

    public Integer readInt(Object obj, String... names) {
        if (obj == null) return null;
        if (obj instanceof Map<?,?> map) {
            for (String n : names) {
                Object v = map.get(n);
                Integer i = coerceToInt(v);
                if (i != null) return i;
            }
        }
        Class<?> c = obj.getClass();
        for (String n : names) {
            for (String cand : new String[]{"get" + n, "get" + capitalize(n)}) {
                try {
                    Method m = c.getMethod(cand);
                    Object v = m.invoke(obj);
                    Integer i = coerceToInt(v);
                    if (i != null) return i;
                } catch (NoSuchMethodException ignore) {
                } catch (Exception ignore) { }
            }
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                Integer i = coerceToInt(v);
                if (i != null) return i;
            } catch (NoSuchFieldException ignore) {
            } catch (Exception ignore) { }
        }
        return null;
    }

    public Integer coerceToInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            String digits = s.replaceAll("[^0-9-]", "");
            if (digits.isEmpty() || digits.equals("-")) return null;
            try { return Integer.parseInt(digits); } catch (Exception ignore) {}
        }
        return null;
    }

    public ChatResponse buildResponseWithMessages(String sessionId,
                                                  String answer,
                                                  String audioDataUrl,
                                                  String audioBase64,
                                                  Map<String, Object> responseMeta) {
        ChatResponse resp = createBlankResponse(answer);

        Map<String, Object> aiMsg = new LinkedHashMap<>();
        aiMsg.put("type", "ai");
        aiMsg.put("role", "assistant");
        aiMsg.put("message", answer);
        aiMsg.put("content", answer);
        aiMsg.put("text", answer);
        aiMsg.put("answer", answer);
        aiMsg.put("response", answer);

        Map<String, Object> meta = responseMeta;
        if (meta != null && !meta.isEmpty()) {
            aiMsg.put("meta", meta);
        }

        if (audioDataUrl != null) {
            aiMsg.put("audioUrl", audioDataUrl);
            aiMsg.put("voiceUrl", audioDataUrl);
            aiMsg.put("ttsUrl", audioDataUrl);
        }
        if (audioBase64 != null) {
            aiMsg.put("audioBase64", audioBase64);
            aiMsg.put("audio", audioBase64);
            aiMsg.put("speechBase64", audioBase64);
        }

        List<Map<String, Object>> messages = List.of(aiMsg);

        boolean msgAttached = attachListToResponse(resp, messages,
                new String[]{"setMessages", "setMessageList", "setResponses", "setItems"},
                new String[]{"messages", "messageList", "responses", "items", "queue"});
        if (!msgAttached) {
            attachStringToResponse(resp, answer,
                    new String[]{"setMessage", "setContent", "setAnswer", "setText", "setResponse"},
                    new String[]{"message", "content", "answer", "text", "response"},
                    null, null);
            if (audioDataUrl != null) {
                embedAudioDataUrlIntoMessage(resp, audioDataUrl);
            }
        }

        attachStringToResponse(resp, sessionId,
                new String[]{"setSessionId"},
                new String[]{"sessionId"},
                null, null);

        if (meta != null && !meta.isEmpty()) {

            attachMapToResponse(resp, meta,
                    new String[]{"setMeta"},
                    new String[]{"meta"});

            Object recMenus = meta.get("recommendedMenus");
            if (recMenus instanceof List<?> list && !list.isEmpty()) {
                attachListToResponse(resp, list,
                        new String[]{"setRecommendedMenus"},
                        new String[]{"recommendedMenus", "menus"});
            }
            Object recIds = meta.get("recommendedMenuIds");
            if (recIds instanceof List<?> list2 && !list2.isEmpty()) {
                attachListToResponse(resp, list2,
                        new String[]{"setRecommendedMenuIds"},
                        new String[]{"recommendedMenuIds", "menuIds"});
            }

            Object cartOps = meta.get("cartOps");
            if (cartOps instanceof Map<?,?> cartMapRaw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cartMap = (Map<String, Object>) cartMapRaw;
                attachMapToResponse(resp, cartMap,
                        new String[]{"setCartOps"},
                        new String[]{"cartOps"});
                try {
                    String cartOpsJson = objectMapper.writeValueAsString(cartOps);
                    attachStringToResponse(resp, cartOpsJson,
                            new String[]{"setCartOpsJson"},
                            new String[]{"cartOpsJson"},
                            null, null);
                } catch (Exception ignore) {}
            }

            try {
                String metaJson = objectMapper.writeValueAsString(meta);
                attachStringToResponse(resp, metaJson,
                        new String[]{"setMetaJson"},
                        new String[]{"metaJson"},
                        null, null);
            } catch (Exception ignore) {}
        }

        return resp;
    }

    public ChatResponse createBlankResponse(String textSeed) {
        ChatResponse r;
        r = tryRecordCtor(textSeed);
        if (r != null) return r;

        r = trySingleStringCtor(textSeed);
        if (r != null) return r;

        r = tryBuilder(textSeed);
        if (r != null) return r;

        r = tryNoArgsThenSet(textSeed);
        if (r != null) return r;

        r = tryArbitraryCtor(textSeed);
        if (r != null) return r;

        r = tryJackson(textSeed);
        if (r != null) return r;

        try {
            var ctor = ChatResponse.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private ChatResponse tryRecordCtor(String text) {
        try {
            Class<ChatResponse> clazz = ChatResponse.class;
            if (!clazz.isRecord()) return null;
            var comps = clazz.getRecordComponents();
            Class<?>[] ptypes = Arrays.stream(comps).map(rc -> rc.getType()).toArray(Class[]::new);
            Object[] args = new Object[comps.length];
            boolean placed = false;
            for (int i = 0; i < comps.length; i++) {
                if (!placed && comps[i].getType() == String.class) {
                    args[i] = text;
                    placed = true;
                } else {
                    args[i] = defaultValueForType(comps[i].getType());
                }
            }
            Constructor<ChatResponse> ctor = clazz.getDeclaredConstructor(ptypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception ignore) { return null; }
    }

    private ChatResponse trySingleStringCtor(String text) {
        try {
            for (Constructor<?> c : ChatResponse.class.getDeclaredConstructors()) {
                var p = c.getParameterTypes();
                if (p.length == 1 && p[0] == String.class) {
                    c.setAccessible(true);
                    Object o = c.newInstance(text);
                    if (o instanceof ChatResponse) return (ChatResponse) o;
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private ChatResponse tryBuilder(String text) {
        try {
            Object builder = null;
            for (String m : new String[]{"builder", "newBuilder"}) {
                try {
                    builder = ChatResponse.class.getMethod(m).invoke(null);
                    if (builder != null) break;
                } catch (NoSuchMethodException ignore) {}
            }
            if (builder == null) return null;

            boolean setOk = false;
            for (String n : new String[]{"message", "content", "answer", "text", "response"}) {
                try {
                    Method set = builder.getClass().getMethod(n, String.class);
                    set.invoke(builder, text);
                    setOk = true;
                    break;
                } catch (NoSuchMethodException ignore) {}
            }
            if (!setOk) return null;

            Method build = builder.getClass().getMethod("build");
            Object obj = build.invoke(builder);
            if (obj instanceof ChatResponse) return (ChatResponse) obj;
        } catch (Exception ignore) {}
        return null;
    }

    private ChatResponse tryNoArgsThenSet(String text) {
        try {
            var ctor = ChatResponse.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ChatResponse inst = ctor.newInstance();

            for (String s : new String[]{"setMessage", "setContent", "setAnswer", "setText", "setResponse"}) {
                try {
                    Method m = inst.getClass().getMethod(s, String.class);
                    m.invoke(inst, text);
                    return inst;
                } catch (NoSuchMethodException ignore) {}
            }

            var fields = inst.getClass().getDeclaredFields();
            for (var f : fields) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    f.set(inst, text);
                    return inst;
                }
            }
            return inst;
        } catch (NoSuchMethodException ignore) {
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private ChatResponse tryArbitraryCtor(String text) {
        try {
            for (Constructor<?> c : ChatResponse.class.getDeclaredConstructors()) {
                var p = c.getParameterTypes();
                if (p.length == 0) continue;
                Object[] args = new Object[p.length];
                boolean hasString = false;
                for (int i = 0; i < p.length; i++) {
                    if (!hasString && p[i] == String.class) {
                        args[i] = text;
                        hasString = true;
                    } else {
                        args[i] = defaultValueForType(p[i]);
                    }
                }
                if (!hasString) continue;
                c.setAccessible(true);
                Object o = c.newInstance(args);
                if (o instanceof ChatResponse) return (ChatResponse) o;
            }
        } catch (Exception ignore) {}
        return null;
    }

    private ChatResponse tryJackson(String text) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String k : new String[]{"message", "content", "answer", "text", "response"}) m.put(k, text);
            return objectMapper.convertValue(m, ChatResponse.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Object defaultValueForType(Class<?> t) {
        if (t == null) return null;
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == byte.class) return (byte) 0;
        if (t == short.class) return (short) 0;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == char.class) return '\0';
        return null;
    }

    private boolean attachListToResponse(ChatResponse resp,
                                         List<?> value,
                                         String[] setterNames,
                                         String[] fieldNames) {
        if (resp == null || value == null) return false;

        for (String s : setterNames) {
            try {
                Method m = findSetter(resp.getClass(), s, List.class, Collection.class);
                if (m != null) {
                    m.invoke(resp, value);
                    return true;
                }
            } catch (Exception ignore) { }
        }
        try {
            var fields = resp.getClass().getDeclaredFields();
            for (var f : fields) {
                if (Arrays.asList(fieldNames).contains(f.getName())
                        && (List.class.isAssignableFrom(f.getType()) || Collection.class.isAssignableFrom(f.getType()))) {
                    f.setAccessible(true);
                    f.set(resp, value);
                    return true;
                }
            }
        } catch (Exception ignore) { }
        return false;
    }

    public Method findSetter(Class<?> cls, String name, Class<?>... acceptedParamTypes) {
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] pts = m.getParameterTypes();
            if (pts.length == 1) {
                for (Class<?> acc : acceptedParamTypes) {
                    if (acc.isAssignableFrom(pts[0])) return m;
                }
            }
        }
        return null;
    }

    private boolean attachStringToResponse(ChatResponse resp,
                                           String value,
                                           String[] setterNames,
                                           String[] fieldNames,
                                           String setterLogFmt,
                                           String fieldLogFmt) {
        if (resp == null || value == null) return false;

        for (String s : setterNames) {
            try {
                Method m = resp.getClass().getMethod(s, String.class);
                m.invoke(resp, value);
                return true;
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ignore) {
            }
        }

        try {
            var fields = resp.getClass().getDeclaredFields();
            for (var f : fields) {
                if (f.getType() == String.class && Arrays.asList(fieldNames).contains(f.getName())) {
                    f.setAccessible(true);
                    f.set(resp, value);
                    return true;
                }
            }
        } catch (Exception ignore) { }
        return false;
    }

    private boolean attachMapToResponse(ChatResponse resp,
                                        Map<String, Object> value,
                                        String[] setterNames,
                                        String[] fieldNames) {
        if (resp == null || value == null) return false;

        for (String s : setterNames) {
            try {
                Method m = resp.getClass().getMethod(s, Map.class);
                m.invoke(resp, value);
                return true;
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ignore) {
            }
        }

        try {
            var fields = resp.getClass().getDeclaredFields();
            for (var f : fields) {
                if (Map.class.isAssignableFrom(f.getType()) && Arrays.asList(fieldNames).contains(f.getName())) {
                    f.setAccessible(true);
                    f.set(resp, value);
                    return true;
                }
            }
        } catch (Exception ignore) { }
        return false;
    }

    private boolean embedAudioDataUrlIntoMessage(ChatResponse resp, String dataUrl) {
        if (resp == null || dataUrl == null) return false;

        String current = getMessageLikeValue(resp);
        String merged = (current == null || current.isBlank())
                ? "[[AUDIO_DATA_URL]]" + dataUrl
                : current + "\n[[AUDIO_DATA_URL]]" + dataUrl;

        for (String s : new String[]{"setMessage", "setContent", "setAnswer", "setText", "setResponse"}) {
            try {
                Method m = resp.getClass().getMethod(s, String.class);
                m.invoke(resp, merged);
                return true;
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ignore) { }
        }

        try {
            var fields = resp.getClass().getDeclaredFields();
            for (var f : fields) {
                if (f.getType() == String.class) {
                    f.setAccessible(true);
                    f.set(resp, merged);
                    return true;
                }
            }
        } catch (Exception ignore) { }
        return false;
    }

    private String getMessageLikeValue(Object obj) {
        if (obj == null) return null;
        for (String g : new String[]{"getMessage", "getContent", "getAnswer", "getText", "getResponse"}) {
            try {
                Method m = obj.getClass().getMethod(g);
                Object v = m.invoke(obj);
                if (v != null) return String.valueOf(v);
            } catch (NoSuchMethodException ignore) {
            } catch (Exception ignore) { }
        }
        return null;
    }

    public String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    public boolean containsAny(String s, String... keys) {
        if (s == null) return false;
        String ls = s.toLowerCase(Locale.ROOT);
        for (String k : keys) {
            if (ls.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public boolean equalsAny(String s, String... keys) {
        if (s == null) return false;
        for (String k : keys) if (s.equalsIgnoreCase(k)) return true;
        return false;
    }

    public String firstNonEmpty(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    public boolean isBlank(String s) { return s == null || s.isBlank(); }

    public String capitalize(String n) {
        if (n == null || n.isEmpty()) return n;
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public String formatPrice(int price) {
        return NumberFormat.getInstance(Locale.KOREA).format(price);
    }
}
