// StudyLock 설치 도구 — 브라우저에서 WebUSB(ADB)로 폰에 설치 + 기기관리자 등록
// ya-webadb(Tango) 를 esm.sh 로 로드. 실패해도 하단 '수동 설치'로 항상 대체 가능.
// 패키지마다 최신 버전이 다름 → 각각 정확히 지정 + 공유 의존성(adb/stream-extra) 강제 정렬(?deps).
const VER = { adb: "2.6.0", usb: "2.3.2", cred: "2.1.0", stream: "2.6.1" };
const APK_URL = "./StudyLock.apk";
const ADMIN = "com.studylock/.AdminReceiver";
const TMP = "/data/local/tmp/studylock.apk";

const $ = (id) => document.getElementById(id);
const statusBox = $("status"), logEl = $("log");
const connectBtn = $("connectBtn"), installBtn = $("installBtn"), retryOwnerBtn = $("retryOwnerBtn"), aiSolveBtn = $("aiSolveBtn");
const checks = ["c1", "c2", "c3"].map($);

let adb = null;

function log(msg, cls = "ln-dim") {
  statusBox.classList.add("show");
  const span = document.createElement("span");
  span.className = cls; span.textContent = msg + "\n";
  logEl.appendChild(span); logEl.scrollTop = logEl.scrollHeight;
}
const ok = (m) => log("✓ " + m, "ln-ok");
const err = (m) => log("✕ " + m, "ln-err");

// ---- 지원 여부 + 체크박스 게이트 ----
const webusbOK = !!(navigator.usb);
if (!webusbOK) {
  $("unsupported").style.display = "block";
}
function refreshGate() {
  const allChecked = checks.every((c) => c.checked);
  connectBtn.disabled = !(webusbOK && allChecked);
}
checks.forEach((c) => c.addEventListener("change", refreshGate));
refreshGate();

// ---- ya-webadb 동적 로드 ----
let AdbMod = null;
let preloadStarted = false;
// requestDevice 는 반드시 클릭(user gesture) 안에서 'await 없이' 호출돼야 함.
// → 라이브러리를 미리 로드해둬서 클릭 땐 곧바로 requestDevice 를 부르게 한다.
function preload() {
  if (preloadStarted || !webusbOK) return;
  preloadStarted = true;
  loadAdb().catch(() => { preloadStarted = false; });
}
if (webusbOK) preload();
checks.forEach((c) => c.addEventListener("change", preload)); // 상호작용 시에도 보장
async function loadAdb() {
  if (AdbMod) return AdbMod;
  log("라이브러리 불러오는 중…");
  // 로컬에 미리 번들해둔 파일 1개만 받음(esm.sh 워터폴 제거 → 즉시)
  const m = await import("./tango.js");
  AdbMod = {
    Adb: m.Adb,
    AdbDaemonTransport: m.AdbDaemonTransport,
    Manager: m.AdbDaemonWebUsbDeviceManager?.BROWSER,
    Credential: m.AdbWebCredentialStore,
    Consumable: m.Consumable,
  };
  ok("준비 완료 — 체크 3개 후 '① 폰 연결'을 누르세요");
  return AdbMod;
}

// ---- shell 실행(버전별 API 관용 처리) ----
async function sh(cmd) {
  const sp = adb.subprocess;
  try {
    if (sp.noneProtocol?.spawnWaitText) return await sp.noneProtocol.spawnWaitText(cmd);
    if (sp.shellProtocol?.spawnWaitText) return await sp.shellProtocol.spawnWaitText(cmd);
    if (sp.spawnAndWaitLegacy) return (await sp.spawnAndWaitLegacy(cmd)).stdout ?? "";
  } catch (e) { throw e; }
  throw new Error("이 라이브러리 버전에서 shell API를 찾지 못했어요.");
}

// ---- 연결 ----
connectBtn.addEventListener("click", async () => {
  // requestDevice 는 user gesture 안에서 'await 없이' 첫 호출돼야 함.
  const M = AdbMod;
  if (!M) {
    log("라이브러리 준비 중이에요. 1~2초 뒤 다시 눌러주세요…");
    preload();
    return;
  }
  if (!M.Manager) { err("WebUSB 매니저를 만들 수 없어요. 데스크톱 Chrome/Edge 인지 확인."); return; }
  try {
    connectBtn.disabled = true;
    log("USB 기기 선택 창을 엽니다…");
    const device = await M.Manager.requestDevice();   // ← 클릭 직후 첫 await = requestDevice (제스처 유지)
    if (!device) { log("선택이 취소됐어요."); connectBtn.disabled = false; return; }
    const connection = await device.connect();
    log("폰에서 'USB 디버깅 허용' 팝업이 뜨면 허용을 눌러주세요…");
    const credentialStore = new M.Credential("StudyLock");
    const transport = await M.AdbDaemonTransport.authenticate({ serial: device.serial, connection, credentialStore });
    adb = new M.Adb(transport);
    const model = (await sh("getprop ro.product.model")).trim();
    const ver = (await sh("getprop ro.build.version.release")).trim();
    ok(`연결됨: ${model || "기기"} (Android ${ver || "?"})`);
    installBtn.disabled = false;
    if (retryOwnerBtn) retryOwnerBtn.disabled = false;
  } catch (e) {
    err(`연결 실패 [${e?.name || "Error"}]: ${e?.message || e}`);
    if (e?.stack) log(String(e.stack).split("\n").slice(0, 3).join("\n"), "ln-dim");
    log("→ USB 디버깅 켰는지 / 팝업 허용했는지 / 케이블(데이터 지원) 확인.", "ln-dim");
    connectBtn.disabled = false;
  }
});

// ---- 설치 + 기기관리자 등록 ----
installBtn.addEventListener("click", async () => {
  if (!adb) return;
  installBtn.disabled = true; connectBtn.disabled = true;
  try {
    // 1) APK 다운로드
    log("APK 내려받는 중…");
    const resp = await fetch(APK_URL);
    if (!resp.ok) throw new Error("APK를 못 찾음 (StudyLock.apk 호스팅 확인)");
    const blob = await resp.blob();
    ok(`APK 준비 (${(blob.size / 1048576).toFixed(1)} MB)`);

    // 2) 폰으로 밀어넣기 (sync)
    log("폰으로 전송 중…");
    const sync = await adb.sync();
    const { Consumable } = AdbMod;
    const src = blob.stream().pipeThrough(new TransformStream({
      transform(chunk, ctrl) { ctrl.enqueue(Consumable ? new Consumable(chunk) : chunk); }
    }));
    await sync.write({ filename: TMP, file: src });
    await sync.dispose?.();
    ok("전송 완료");

    // 3) 설치
    log("설치 중…");
    const inst = await sh(`pm install -r -g ${TMP}`);
    if (/Success/i.test(inst) || inst.trim() === "") ok("설치 완료");
    else { log(inst, "ln-dim"); ok("설치 시도 완료"); }
    await sh(`rm -f ${TMP}`).catch(() => {});

    // 4) 기기관리자(device-owner) 등록
    await setDeviceOwner();
  } catch (e) {
    err("실패: " + (e?.message || e));
    log("→ 아래 '수동 설치(adb)' 방법으로도 진행할 수 있어요.", "ln-dim");
  } finally {
    installBtn.disabled = false; connectBtn.disabled = false;
    if (retryOwnerBtn) retryOwnerBtn.disabled = false;
  }
});

// ---- 기기관리자 등록 (+ 실패 시 남은 계정 진단) ----
async function setDeviceOwner() {
  log("기기관리자 등록 중…");
  const dpm = await sh(`dpm set-device-owner ${ADMIN}`);
  if (/Success/i.test(dpm)) {
    ok("기기관리자 등록 성공!");
    await enableGuardAccessibility();
    log("──────────────", "ln-dim");
    ok("완료! 폰에서 StudyLock 을 열어 목표일·허용앱을 설정하세요.");
    return true;
  }
  err("기기관리자 등록 실패");
  log((dpm.split("\n").find(l => l.includes("Exception")) || dpm.split("\n")[0] || dpm).trim(), "ln-dim");
  if (/account/i.test(dpm)) {
    log("남아있는 계정 확인 중…");
    const acc = await sh("dumpsys account").catch(() => "");
    const found = acc.match(/Account\s*\{[^}]*\}/g) || [];
    if (found.length) {
      log(`이 계정들이 막고 있어요 (${found.length}개) — 설정에 안 보여도 존재:`, "ln-err");
      [...new Set(found)].slice(0, 30).forEach(a => log("  " + a, "ln-dim"));
      log("→ type=com.google → 구글 / com.osp.app.signin → 삼성 / 그 외 → 그 앱이 만든 계정", "ln-dim");
      log("→ 설정→계정에서 제거. 안 보이면 해당 앱 삭제. 그래도 남으면 공장초기화가 확실.", "ln-dim");
    } else {
      log("계정이 안 잡히는데도 막힘 → 폰 재부팅 후 재시도, 안 되면 공장초기화.", "ln-dim");
    }
    log("정리했으면 → '③ 기기관리자 재시도' 누르면 돼요 (재설치 불필요).", "ln-dim");
  }
  if (aiSolveBtn) aiSolveBtn.style.display = "block";   // AI 자동해결 버튼 노출
  return false;
}

// 구글 검색 차단 가드(접근성) 자동 활성화 — 앱이 스스로 못 켜서 설치 때 켜준다
async function enableGuardAccessibility() {
  const svc = "com.studylock/com.studylock.GoogleGuardService";
  try {
    let cur = (await sh("settings get secure enabled_accessibility_services").catch(() => "")).trim();
    if (!cur || cur === "null") cur = "";
    const has = cur.split(":").includes(svc);
    const next = has ? cur : (cur ? cur + ":" + svc : svc);
    await sh(`settings put secure enabled_accessibility_services ${next}`);
    await sh("settings put secure accessibility_enabled 1");
    ok("구글 앱 검색 차단(접근성)도 켰어요.");
  } catch (e) {
    log("접근성 자동 활성화는 실패 — 앱 안 안내(모달)에서 켤 수 있어요.", "ln-dim");
  }
}

// ③ 기기관리자만 재시도 (계정 정리 후)
retryOwnerBtn?.addEventListener("click", async () => {
  if (!adb) { log("먼저 '① 폰 연결'을 해주세요."); return; }
  retryOwnerBtn.disabled = true;
  try { await setDeviceOwner(); }
  catch (e) { err("실패: " + (e?.message || e)); }
  finally { retryOwnerBtn.disabled = false; }
});

// ==================== AI 빠른 해결 (Gemini) ====================
// 키는 코드에 넣지 않음 — Netlify 서버리스 함수(환경변수 GEMINI_KEY)가 프록시. (노출 방지)
async function gemini(contents) {
  const body = JSON.stringify({ contents, generationConfig: { temperature: 0.2, responseMimeType: "application/json" } });
  let last;
  for (let attempt = 0; attempt < 3; attempt++) {                 // 5xx(일시 오류)면 재시도
    const r = await fetch("/.netlify/functions/gemini", {
      method: "POST", headers: { "Content-Type": "application/json" }, body,
    });
    const d = await r.json().catch(() => ({}));
    if (r.ok) return d?.candidates?.[0]?.content?.parts?.[0]?.text || "";
    last = `Gemini ${r.status}: ${d?.error?.message || d?.error || "요청 실패"}`;
    if (r.status < 500) break;                                    // 4xx면 재시도 무의미
    await new Promise((res) => setTimeout(res, 1200));
  }
  throw new Error(last);
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function currentState(retries = 4) {
  // 계정 삭제·앱 제거가 시스템에 반영되는 데 몇 초 걸림 → 딜레이 두고 재시도
  let dpm = "";
  for (let i = 0; i < retries; i++) {
    dpm = await sh(`dpm set-device-owner ${ADMIN}`).catch((e) => String(e?.message || e));
    if (/Success/i.test(dpm)) break;
    if (i < retries - 1) await wait(1600);
  }
  const acc = await sh("dumpsys account").catch(() => "");
  const list = [...new Set(acc.match(/Account\s*\{[^}]*\}/g) || [])].join("\n");
  return { dpm, list };
}

const APP_NAMES = {
  "com.instagram.android": "인스타그램",
  "com.facebook.katana": "페이스북",
  "com.google.android.youtube": "유튜브",
  "com.twitter.android": "X(트위터)",
  "com.snapchat.android": "스냅챗",
  "com.zhiliaoapp.musically": "틱톡",
  "com.nhn.android.search": "네이버",
  "com.kakao.talk": "카카오톡",
};
const appName = (p) => APP_NAMES[p] || p;
const BAK_DIR = "/data/local/tmp/sl_bak";

// 지우기 전에 APK(스플릿 포함)를 폰 내부에 백업. 성공 true.
async function backupApk(pkg) {
  try {
    const paths = [...(await sh(`pm path ${pkg}`)).matchAll(/package:(\/\S+\.apk)/g)].map((m) => m[1]);
    if (!paths.length) return false;
    const dir = `${BAK_DIR}/${pkg}`;
    await sh(`rm -rf ${dir}; mkdir -p ${dir}`);
    for (const p of paths) await sh(`cp "${p}" ${dir}/`);
    return /\.apk/.test(await sh(`ls ${dir}`).catch(() => ""));
  } catch { return false; }
}

// 백업한 APK로 재설치(install 세션 → 스플릿 다 write → commit). 성공 true.
async function restoreApk(pkg) {
  const dir = `${BAK_DIR}/${pkg}`;
  const apks = (await sh(`ls ${dir}`).catch(() => "")).split(/\s+/).filter((f) => f.endsWith(".apk"));
  if (!apks.length) return false;
  const sid = ((await sh(`pm install-create`).catch(() => "")).match(/\[(\d+)\]/) || [])[1];
  if (!sid) return false;
  for (let i = 0; i < apks.length; i++) await sh(`pm install-write ${sid} split${i} ${dir}/${apks[i]}`);
  const commit = await sh(`pm install-commit ${sid}`).catch((e) => String(e));
  await sh(`rm -rf ${dir}`).catch(() => {});
  return /Success/i.test(commit);
}

// 성공 후: 지운 앱을 백업해둔 APK로 복원(계정·인터넷 없이). 백업 없으면 플레이스토어.
async function offerReinstall(removed) {
  if (!removed.length) return;
  const names = removed.map((r) => appName(r.pkg)).join(", ");
  if (!confirm(`AI가 문제 해결하며 지운 앱: ${names}\n\n다시 설치할까요?\n(지우기 전 백업해둔 그대로 복원 — 로그인만 다시 하면 돼요)`)) {
    log(`복원 안 함. 필요하면 나중에 설치: ${names}`, "ln-dim");
    return;
  }
  for (const r of removed) {
    if (r.backedUp) {
      log(`${appName(r.pkg)} 복원 설치 중…`, "ln-dim");
      if (await restoreApk(r.pkg)) { ok(`${appName(r.pkg)} 다시 설치됨 — 폰에서 로그인만 하면 돼요.`); continue; }
      err(`${appName(r.pkg)} 복원 실패 — 플레이스토어로 시도해요`);
    }
    await sh(`am start -a android.intent.action.VIEW -d 'market://details?id=${r.pkg}'`).catch(() => {});
    log(`${appName(r.pkg)}: 폰 플레이스토어를 열었어요('설치' 누르기, 구글 로그인 필요)`, "ln-dim");
    await wait(700);
  }
}

async function aiSolve() {
  if (!adb) { log("먼저 '① 폰 연결'을 해주세요."); return; }
  aiSolveBtn.disabled = true;
  try {
    log("AI가 상황을 파악하는 중…");
    let st = await currentState(1);   // 첫 확인은 1회(계정 있는 게 당연)
    if (/Success/i.test(st.dpm)) { await enableGuardAccessibility(); ok("기기관리자 등록 성공! 폰에서 StudyLock 을 열어 설정하세요."); return; }

    const sys = `너는 안드로이드 '기기관리자(device owner)' 설정을 끝까지 자동으로 해결하는 어시스턴트야.
사용자는 브라우저(WebADB)로 폰에 연결돼 있고, 네가 제안하는 shell 명령을 (사용자 허가 후) 실행하고 결과를 매번 너에게 다시 줄 거야. 성공할 때까지 한 단계씩 계속 명령을 제안해.
목표: 'dpm set-device-owner ${ADMIN}' 가 Success 나오게 만드는 것.
원인: 폰에 계정이 남아있음. 앱이 만든 계정(예: type=www.instagram.com → 패키지 com.instagram.android)을 없애야 함. 계정 type 으로 패키지 추정, 애매하면 'pm list packages | grep <키워드>' 로 찾아.
해결 순서(한 계정당):
 1) 'pm clear <패키지>' 로 앱 데이터 초기화(= 이 폰에서 로그아웃) → 시스템이 자동으로 dpm 재시도.
 2) 그래도 그 계정이 남아있으면 'pm uninstall <패키지>' 로 그 앱을 폰에서 제거(그러면 계정도 사라짐). 스터디 잠금폰이니 앱 제거는 괜찮음.
계정이 여러 개면 다 없앨 때까지 반복.
아주 중요(사용자 안심): explanation 은 절대 무섭게 쓰지 마. 이건 '온라인 계정 탈퇴/삭제'가 아니라 '이 폰에서 로그아웃하거나 앱을 지우는 것'뿐이고, 게시물·팔로워·계정은 서버에 그대로 안전하며 아이디/비번으로 언제든 다시 로그인된다는 점을 쉽고 안심되게 설명해. 예: "인스타 계정을 없애는 게 아니라, 이 폰에서만 로그아웃돼요. 온라인 계정과 사진·팔로워는 그대로 안전하고 나중에 다시 로그인하면 돼요."
규칙:
- 한 번에 명령 딱 하나.
- 허용 명령: pm clear / pm uninstall / pm list packages / dumpsys account 만. 금지: reboot / wipe / 초기화 / rm / settings put.
- 반드시 아래 JSON 만 출력(다른 텍스트 금지):
{"explanation":"이 명령이 뭘 하는지 + 사용자 안심 문구 포함, 한국어 2~3문장","command":"'adb shell' 뒤 명령. 더 할 게 없으면 null"}`;

    const contents = [
      { role: "user", parts: [{ text: `${sys}\n\n[현재 상황]\ndpm 결과: ${st.dpm}\n남은 계정:\n${st.list || "(계정 목록 못 읽음)"}` }] },
    ];

    const removed = [];   // AI가 지운 앱(성공 후 다시 설치 제안용)
    for (let step = 0; step < 12; step++) {
      log("AI 생각 중…", "ln-dim");
      let raw = await gemini(contents);
      let j;
      try { j = JSON.parse(raw); }
      catch {
        contents.push({ role: "model", parts: [{ text: raw }] });
        contents.push({ role: "user", parts: [{ text: "JSON 형식으로만 다시 답해줘." }] });
        continue;
      }
      contents.push({ role: "model", parts: [{ text: raw }] });

      if (!j.command) { log("AI: " + (j.explanation || "더 할 게 없음")); log("→ 자동으로 안 되면 폰 설정에서 계정 수동 제거 후 '③ 재시도'.", "ln-dim"); break; }

      log("AI 제안: " + j.explanation);
      if (!confirm(`AI 제안\n\n${j.explanation}\n\n실행할 명령:\n  adb shell ${j.command}\n\n실행할까요?`)) {
        log("사용자가 취소함. (다시 'AI 빠른 해결' 누르면 이어서 진행)", "ln-dim"); break;
      }
      // 앱을 지우는 명령이면 먼저 APK 백업(성공 후 그대로 복원하려고)
      const um = j.command.match(/pm\s+uninstall\s+(?:-\S+\s+)*([\w.]+)/);
      let bak = false;
      if (um) {
        log(`${appName(um[1])} APK 백업 중… (복원용)`, "ln-dim");
        bak = await backupApk(um[1]);
        log(bak ? "APK 백업 완료" : "APK 백업 실패(복원은 플레이스토어로)", "ln-dim");
      }

      log("실행: " + j.command, "ln-dim");
      const out = await sh(j.command).catch((e) => String(e?.message || e));
      log("결과: " + (out.trim().slice(0, 300) || "(빈 출력)"), "ln-dim");

      if (um && /Success/i.test(out) && !removed.some((r) => r.pkg === um[1])) {
        removed.push({ pkg: um[1], backedUp: bak });
      } else if (um && bak && !/Success/i.test(out)) {
        await sh(`rm -rf ${BAK_DIR}/${um[1]}`).catch(() => {});   // 삭제 실패면 백업 정리
      }

      // 명령 후 자동으로 기기관리자 재시도 + 상태 갱신 (반영까지 몇 초 걸려 재시도)
      log("기기관리자 재시도 중… (반영 대기, 몇 초 걸려요)", "ln-dim");
      st = await currentState();
      if (/Success/i.test(st.dpm)) {
        ok("기기관리자 등록 성공! 폰에서 StudyLock 을 열어 설정하세요.");
        await offerReinstall(removed);
        break;
      }
      log("아직 실패 — 남은 계정: " + (st.list ? st.list.replace(/\n/g, " / ") : "(없음)"), "ln-dim");
      contents.push({ role: "user", parts: [{ text: `방금 명령 결과:\n${out}\n\ndpm 재시도 결과: ${st.dpm}\n남은 계정:\n${st.list || "(없음)"}\n\n아직 실패면 다음 명령을, 더 할 게 없으면 command=null.` }] });

      if (step === 11) err("여러 번 시도했지만 안 됐어요. 남은 계정을 폰 설정에서 직접 제거하거나 공장초기화가 필요할 수 있어요.");
    }
  } catch (e) {
    err("AI 해결 실패: " + (e?.message || e));
    log("→ 키/네트워크 문제면, 인스타 등 계정 앱을 설정에서 로그아웃 후 '③ 기기관리자 재시도'.", "ln-dim");
  } finally {
    aiSolveBtn.disabled = false;
  }
}
aiSolveBtn?.addEventListener("click", aiSolve);
