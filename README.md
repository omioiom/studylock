# StudyLock

수능 공부하려고 만든 학습 잠금 앱. Device Owner + Lock Task 로 키오스크처럼 잠가서, 목표일까지 내가 허용한 앱만 열린다. 나가려면 PIN 아니면 공장초기화뿐.

브라우저에서 USB로 연결하면 adb 안 쳐도 깔린다: https://studylock.kro.kr
패키지는 `com.studylock`, 흑백 Compose UI, 서버 없음.

## 뭐가 되나

- 허용한 앱만 열림. 홈/뒤로가기/앱삭제/안전모드/시계 앞당기기/멀티유저 다 막음
- 시간표 (블록 편집, 유형 태그, D-day, 다음 일정까지 남은 시간)
- 스크린타임 (앱별·전체 하루 제한, 시간표 일정별 차단, 예약 해제창)
- 집중 잠금 (정한 시간 동안 전화만 되게)
- 일정 알림 (시작/종료/변경)
- 잠금 중엔 시스템 퀵설정을 못 여니까 앱 안에 Wi-Fi·데이터·밝기·블루투스·방해금지·설정 버튼을 따로 둠
- 허용앱 길게 누르면 제외/삭제/앱설정/앱별제한
- 화면 잠금(PIN·패턴). AOD나 근접센서 때문에 화면 안 껐는데 잠기던 거 디바운스로 막았고, 풀면 직전에 보던 앱으로 돌아감

## 설치

웹툴이 편하다. 폰 USB 디버깅 켜고 studylock.kro.kr 접속 → 폰 연결 → 설치 누르면 됨. 안에서 `pm install -r` 로 깔고 device-owner 등록까지 한다. 데이터는 안 지워진다.

등록되려면 폰에 **계정이 하나도 없어야** 한다 (구글·삼성은 물론이고 인스타 같은 앱 계정도 막는다). 계정 때문에 실패하면 "AI 빠른 해결" 버튼이 뜨는데, Gemini가 뭐가 막는지 보고 지울 명령을 제안한다. **뭘 왜 하는지 알려주고 내가 허가해야** 실행된다. 키는 Netlify 함수(`netlify/functions/gemini.js`)에 두니까 코드엔 안 박혀있다.

adb로 직접 하려면:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dpm set-device-owner com.studylock/.AdminReceiver
```

기존 폰이면 공장초기화하고 구글 계정 추가는 건너뛰어야 한다. 잠그기 전에 카톡·은행 로그인 같은 건 미리 다 해두는 게 낫다. 잠근 뒤엔 설정을 못 건드리니까.

## 빌드

JDK 17, SDK 34.

```bash
./gradlew assembleRelease
```

서명은 `release.keystore`. 웹툴로 뿌리는 apk 는 `docs/StudyLock.apk` 에 복사해둔다.

## 구조

- `app/` — 앱 본체 (Prefs, LockManager, ScreenTime, Timetable, ui/…)
- `docs/` — 웹 설치 도구 (Netlify 정적 호스팅, WebUSB)
- `netlify/functions/gemini.js` — Gemini 프록시


