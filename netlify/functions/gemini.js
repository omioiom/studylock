// Gemini 프록시 — API 키를 서버(환경변수)에만 두고 클라이언트엔 노출 안 함.
// Netlify → Site settings → Environment variables 에 GEMINI_KEY 설정.
// 기본 모델이 과부하(503/429)면 폴백 모델로 자동 재시도.
exports.handler = async (event) => {
  if (event.httpMethod !== "POST") {
    return { statusCode: 405, body: JSON.stringify({ error: "POST only" }) };
  }
  const KEY = process.env.GEMINI_KEY;
  if (!KEY) {
    return { statusCode: 500, body: JSON.stringify({ error: "GEMINI_KEY 환경변수가 설정되지 않았어요 (Netlify 설정)" }) };
  }
  const primary = process.env.GEMINI_MODEL || "gemini-3.5-flash";
  const models = [...new Set([primary, "gemini-2.5-flash", "gemini-flash-latest", "gemini-2.0-flash"])];
  const body = event.body || "{}";

  let last = { statusCode: 502, body: JSON.stringify({ error: "no model responded" }) };
  for (const model of models) {
    try {
      const resp = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
        { method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": KEY }, body }
      );
      const text = await resp.text();
      if (resp.ok) {
        return { statusCode: 200, headers: { "Content-Type": "application/json", "X-Model-Used": model }, body: text };
      }
      last = { statusCode: resp.status, headers: { "Content-Type": "application/json" }, body: text };
      // 과부하/속도제한/일시오류면 다음 모델로 폴백, 그 외(잘못된 요청 등)는 즉시 반환
      if (![429, 500, 503].includes(resp.status)) return last;
    } catch (e) {
      last = { statusCode: 500, body: JSON.stringify({ error: String((e && e.message) || e) }) };
    }
  }
  return last;
};
