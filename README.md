# 댕스(Daengs) — 안드로이드 앱

반려견 케어 플랫폼. 이 저장소는 **홈 화면과 미니룸**을 담당한다.

이 문서는 **돌리는 방법**만 적는다. 나머지는 아래로 나뉘어 있다.

| 문서 | 내용 |
|---|---|
| [`STATUS.md`](STATUS.md) | **지금 어디까지 됐나** · 다음 결정 · 알려진 문제 |
| [`HISTORY.md`](HISTORY.md) | 어떻게 여기까지 왔나. 배경지식 없이 읽을 수 있게 쓴 작업 일지 |
| [`CONTEXT.md`](CONTEXT.md) | 기획·결정 사항 (앱 전체) |
| [`CLAUDE.md`](CLAUDE.md) | 코드 규칙. 사람도 Claude 도 시작 전에 읽는다 |
| [`docs/collaboration.md`](docs/collaboration.md) | **협업 규칙** — 우선순위 · Iteration · PR 기준 · 회고 |

처음이면 **STATUS.md** 부터 보는 게 빠르다. 같이 일하게 됐다면
**docs/collaboration.md** 를 먼저 본다.

---

## 시작하기

### 필요한 것

| | 버전 | 확인 |
|---|---|---|
| JDK | **25** | `gradle/gradle-daemon-jvm.properties` 의 `toolchainVersion` |
| Android SDK | compileSdk **37** / minSdk **26** | `app/build.gradle.kts` |
| Android Studio | 최신 안정판 | |

### `local.properties` 는 저장소에 없다

SDK 경로가 사람마다 달라서 `.gitignore` 에 들어 있다. **Android Studio 로 프로젝트를
열면 자동으로 만들어준다.** 터미널에서 `gradlew` 부터 돌리면
`SDK location not found` 가 나므로, 그때는 최상위에 직접 한 줄 쓴다.

```properties
sdk.dir=C:/Users/<이름>/AppData/Local/Android/Sdk
```

**슬래시(`/`)로 쓰는 게 편하다.** 역슬래시를 쓰면 `C\:\\Users\\...` 처럼 두 번 겹쳐
써야 하고, 하나라도 틀리면 `java.io.IOException: Invalid file path` 가 난다.

### 출시용 업로드 키 (선택)

**없어도 개발에는 지장이 없다.** 릴리즈 빌드가 서명 없이 나가고 경고만 뜬다 —
컴파일과 용량 확인에는 그대로 쓸 수 있고, 설치·업로드만 안 된다.

```properties
daengs.uploadKeyStore=keystore/upload.jks
daengs.uploadKeyAlias=daengs-upload
daengs.uploadKeyPassword=<비밀번호>
```

- **이건 "업로드 키" 다.** Play 앱 서명을 쓰므로 진짜 앱 서명 키는 구글이 만들어
  보관하고, 우리는 올릴 때 신원을 증명하는 이 키만 갖는다. 잃어도 Play Console 에서
  재설정할 수 있다 — 그래도 잃지 않는 편이 낫다
- ⚠️ **키 파일과 비밀번호는 저장소에 안 들어간다.** `.gitignore` 가 `*.jks` 를
  막고 있고 `local.properties` 도 원래 무시된다. **팀원과는 저장소 밖에서 나눈다**
- 새로 만들려면:

  ```bash
  keytool -genkeypair -v -keystore keystore/upload.jks     -alias daengs-upload -keyalg RSA -keysize 4096 -validity 10000
  ```

### 출시 빌드는 다른 서버를 본다

**개발은 지금 개발 서버 그대로 쓰고, 릴리즈만 GCP 의 https 서버를 본다.** 개발
서버가 `http://` 인데 평문 HTTP 허용이 디버그 소스셋에만 있어서, 릴리즈 빌드는 개발
서버로 요청이 소켓 단계에서 죽는다. 그렇다고 개발 서버를 https 로 옮기면 매번 주소를
바꿔 끼워야 한다. 그래서 **빌드 종류로 갈랐다.**

```properties
daengs.apiBaseUrlRelease=https://daengapi.weareithero.cloud
daengs.gaitUrlRelease=https://daengapi.weareithero.cloud/gait
```

- **`daengapi` 한 호스트가 전부를 받는다** — 백엔드(`/`) · 진단(`/screen/`) ·
  보행(`/gait/`) · 장소(`/v2/places/`). `daengapp` 은 웹 프론트용이라 앱은 안 쓴다
- **안 넣어도 빌드는 된다.** 릴리즈가 개발 주소로 떨어지고 **빌드 로그에 경고**가
  뜬다. 조용히 떨어지면 "켜지는데 통신만 죽는" 릴리즈가 나와서 한참 헤맨다
- 그래서 `app/src/debug/AndroidManifest.xml` 은 **지우지 않는다.** 개발 서버가
  `http://` 인 한 디버그에는 계속 필요하다

### 카카오 로그인을 켜려면 (선택)

**안 채워도 앱은 돌아간다.** 랜딩 화면에서 `둘러보기` 를 누르면 방까지 들어가진다.
로그인을 실제로 써 보려면 `local.properties` 에 두 줄을 더 넣는다.

```properties
daengs.kakaoNativeAppKey=<카카오 콘솔의 네이티브 앱 키>
daengs.apiBaseUrl=http://<서버주소>:8000
```

- **네이티브 앱 키**는 백엔드가 쓰는 REST API 키와 **다른 키**다.
  콘솔 → 내 애플리케이션 → 앱 키 → `네이티브 앱 키`.
- 카카오 콘솔 → 플랫폼 → Android 에 **키 해시**를 등록해야 한다.
  패키지명은 `com.daengs.app`, 키 해시는 아래 하나면 된다.

  ```
  c7dmwvr4JCn9xS6xmZnszm8/0Bw=
  ```

  저장소의 `keystore/debug.keystore` 에서 뽑은 값이라 **팀원 전체의 디버그 빌드가
  이거 하나로 된다.** 각자 키를 등록할 필요가 없다 (키를 커밋해 둔 이유가 이것이다).
  직접 확인하려면:

  ```bash
  keytool -exportcert -alias androiddebugkey -keystore keystore/debug.keystore     -storepass android | openssl sha1 -binary | openssl base64
  ```

- 서버는 `http://` 라 평문이다. 안드로이드가 기본으로 막으므로 **디버그 빌드에만**
  풀어 뒀다 (`app/src/debug/AndroidManifest.xml`). 자체 서버라 **폰이 같은 네트워크에
  있어야** 닿는다.

### 피부 진단

**따로 채울 것이 없다.** `daengs.apiBaseUrl` 하나면 된다 — 진단이 우리 서버 안으로
들어와 있고(D-040), 앱은 `/app/screening/…` 한 길만 쓴다. 옛 경로
`/screen/v1/screen` 은 #134 에서 뗐다.

> 예전 `daengs.screenUrl` · `daengs.screenUrlRelease` 는 **더 안 읽는다.**
> local.properties 에 남아 있어도 아무 일도 안 한다.

모델이 살아 있는지는 이걸로 본다.

```bash
curl http://daengback.weareithero.cloud/screen/healthz
# {"ok":true,"mock":false,"contract_version":"1.0","threshold":0.1466...}
```

- 이 `/screen/` 은 **콘솔(웹)이 쓰는 경로**다. 앱은 안 부르지만, 모델이 떠 있는지
  보기에는 여전히 제일 빠르다.
- `mock` 이 `true` 면 가짜 응답이다. 숫자를 믿으면 안 된다.
- **`threshold` 가 모델 판이다.** 재학습해서 갈아끼우면 이 값이 바뀌므로, 서버에
  새 가중치가 물렸는지 여기서 확인한다.
- 서버가 **꺼져 있을 수 있다.** 스크리닝은 `profiles: ["screening"]` 뒤에 있어서
  평소 `docker compose up -d` 로는 안 뜬다. 그때는 앱이 진단을 실패로 알린다.

#### 내 PC 에서 띄워 쓸 때

모델을 직접 고치는 중이라면 [`gayeoniee/deeplearning_test`](https://github.com/gayeoniee/deeplearning_test)
를 받아 돌린다. **앱을 그리로 향하게 하는 길은 이제 없다** — 앱은 backend 만 보고
backend 가 모델을 부른다. 아래는 모델 자체를 확인할 때다.

```bash
uv run --extra train --extra serve python serve.py --release <release폴더> --host 0.0.0.0
```

- **`--host 0.0.0.0` 이 중요하다.** 기본값 `127.0.0.1` 은 그 PC 안에서만 보여서,
  같은 와이파이라도 폰이 못 닿는다.
- USB 로 이어 놨다면 `adb reverse` 로도 된다. 이 다리는 USB 를 뺐다 꽂거나 adb 가
  재시작하면 **말없이 사라진다** — 앱은 잘 떠 있는데 진단만 안 되면 여기부터 본다.

  ```bash
  adb reverse tcp:8000 tcp:8000   # daengs.apiBaseUrl=http://127.0.0.1:8000
  adb reverse --list              # 걸려 있는지 확인
  ```

### 지도를 켜려면 (선택)

**안 채워도 앱은 켜진다.** 지도 타일만 인증 실패로 비고 나머지 화면은 그대로 돈다
(카카오 키와 같은 철학).

```properties
daengs.naverMapClientId=<네이버 클라우드 플랫폼의 Maps 클라이언트 ID>
daengs.naverMapStyleId=<Style Editor 에서 발행한 My Style ID>   # 없어도 됨
```

네이버 클라우드 플랫폼 콘솔 → Maps → 인증 정보. 앱 패키지명 `com.daengs.app` 을
등록해야 그 키로 타일이 나온다.

- 지도 SDK 도 메이븐 센트럴에 없다. `settings.gradle.kts` 가 네이버 저장소를 따로
  열어 두었다 (카카오와 같은 이유).
- **키가 없으면 격자만 뜬다.** 마커·검색·카드는 정상이라 앱이 고장 난 것처럼 보이는데,
  로그에 `NaverMap: Authorization failed: [800] Client is unspecified` 가 찍힌다.
- **스타일 ID 는 있으면 좋은 것이다.** 콘솔 → Maps → Style Editor 에서 지도를 앱
  팔레트로 칠하고 [Publish] 하면 My Style ID 가 나온다. 넣은 색과 편집기 제약은
  [`docs/map-style.md`](docs/map-style.md) 에 있다. **없으면 기본 네이버 지도**로
  뜨고 앱은 그대로 돈다. ID 가 틀리면 조용히 기본 지도가 되므로, 로그에
  `DaengsMap` 태그로 실패를 남겨 둔다.
- **내 주변 탭의 장소 검색은 이 키와 별개다** — 그쪽은 `daengs.apiBaseUrl` 의
  서버를 부른다.

### 산책을 기록하려면

하단 **산책기록** 탭에서 위치 권한을 허용하고 `산책 시작`을 누른다. Android 13 이상은
알림 권한도 묻는다. 알림을 거부해도 기록은 가능하지만 알림창의 일시정지·종료 버튼은
보이지 않을 수 있다.

- 화면을 나가거나 꺼도 위치 Foreground Service가 기록을 이어 간다.
- 지도에는 흔들림과 정확도 낮은 점을 걸러낸 경로가 보인다.
- 기기가 보고한 원본 위치는 `daengs_walk.db`에 먼저 저장한다.
- 현재는 **로컬 기록만 한다.** 백엔드 업로드·점수·영토·기록 목록은 연결하지 않았다.
- 강제 종료로 닫히지 않은 세션은 DB에 남지만, 이어 기록/폐기 화면은 아직 없다.

### 빌드 · 테스트 · 설치

점령지 둘러보기는 기본 debug와 release 모두 서버에서 점령 정보를 읽는다. 로그인과
`GET /app/territory/occupancies`가 배포된 API가 필요하다. 먼 지역의 전봇대를 선택해도
산책 시작 없이 강아지·인증·점령 시각을 볼 수 있다. 조회 실패는 미점유로 표시하지 않는다.
로컬 연습은 debug에 `-PterritoryServerRead=false`, 온라인 액션 테스트는
`-PterritoryServerActions=true`를 명시한다. [설정과 검증](docs/territory-server-browsing.md).

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 에서는 `gradlew.bat` 을 쓴다.

### 스토어에 올릴 빌드

```bash
./gradlew :app:bundleRelease -PversionCode=2 -PversionName=1.0.1
# app/build/outputs/bundle/release/app-release.aab
```

- **APK 가 아니라 AAB 로 올린다.** 기기마다 필요한 CPU 라이브러리만 내려가서, 130MB
  짜리 `libnavermap.so` 네 벌이 한 벌이 된다
- **`-PversionCode` 를 안 주면 `1` 이다.** 평소 빌드가 지금과 똑같아야 해서 그렇게
  뒀지만, **스토어에 올릴 때는 반드시 준다.** Play 는 한 트랙에서 같은 versionCode 를
  두 번 받지 않고, **지운 릴리스가 쓴 번호도 재사용할 수 없다**
- **2026-09-02 의 `v1` 태그가 versionCode 1 로 나갔다. 다음 업로드는 2 부터다**
- `-PversionName` 은 사람이 읽는 값이라 안 줘도 된다 (없으면 `1.0`)
- 숫자가 아닌 값을 주면 **빌드가 멈춘다.** 조용히 1 로 떨어지면 오타 하나가 그대로
  통과해서, 다 만든 AAB 를 올리는 자리에서야 중복으로 거부당한다

⚠️ **카카오 로그인은 키 해시를 하나 더 등록해야 스토어 빌드에서 된다.** Play 앱
서명을 쓰므로 구글이 앱을 다시 서명하고, 그 키의 해시는 저장소의 debug 키에서 뽑은
값과 다르다. Play Console → 설정 → 앱 서명 의 SHA-1 을 base64 로 바꿔 카카오 콘솔에
넣는다 ([카카오 로그인을 켜려면](#카카오-로그인을-켜려면-선택)).

---

## 디버그 서명 키가 왜 커밋되어 있나

`keystore/debug.keystore` 는 **일부러 넣어둔 것**이다. 놀라지 않아도 된다.

PC 마다 다른 `~/.android/debug.keystore` 로 서명되면, 같은 테스트 폰에 이미 깔린 앱을
덮어쓰지 못하고 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 이 난다. 팀이 한 기기를 돌려 쓰면
매번 지웠다 깔아야 한다. 그래서 **팀이 같은 키로 서명하도록** 저장소에 넣었다.

디버그 전용 키이고 비밀번호도 안드로이드 기본값(`android`)이라 공개해도 안전하다.
**릴리스 키는 절대 커밋하지 않는다.**

설정은 `app/build.gradle.kts` 의 `signingConfigs` 에 있다.

---

## 폴더

```
app/          안드로이드 앱 (Kotlin + Jetpack Compose)
  src/main/java/com/daengs/app/
    map/        네이버 지도 표면과 장소·산책 동선 레이어
    miniroom/   미니룸 — 좌표계·배치·강아지·그리기
    ui/         홈 화면, 인벤토리, 개발자 패널
    walk/       산책 기록 코어·Foreground Service·Room 저장 계약
  src/main/res/drawable-nodpi/   픽셀 아트 (WebP)
  src/test/     단위 테스트 84개
tools/        파이썬 도구 (에셋 반입·가공)
docs/         에셋 제작 워크플로, 아이소메트릭 템플릿
design/       화면 시안
keystore/     팀 공용 디버그 서명 키
CONTEXT.md    기획·결정 사항
```

`drawable-nodpi` 를 쓰는 이유는 dpi 스케일링 없이 **원본 픽셀 그대로** 디코드되기
때문이다. 픽셀 아트라 보간이 끼면 뿌옇게 번진다.

---

## 미니룸 코드 읽는 순서

1. **`miniroom/IsoMath.kt`** — 좌표계. 격자 ↔ 화면 변환이 전부 여기서 나온다.
   방 그림에 자를 대서 맞춘 사각형이라, 여기가 어긋나면 나머지가 전부 어긋난다
2. **`miniroom/MiniRoomState.kt`** — 무엇이 어디에 놓여 있나. 칸 점유·앞뒤 정렬
3. **`miniroom/art/ItemCatalog.kt`** — 아트 규격. 소품·강아지의 크기와 기준점
4. **`miniroom/MiniRoomCanvas.kt`** — 실제로 그리는 곳

**화면 오른쪽 위 `DEV` 토글**을 켜면 격자·발자국·강아지 반경이 방 위에 그려진다.
좌표가 어긋날 때 여기부터 본다.

---

## `tools/` — 파이썬 도구

전부 [PEP 723](https://peps.python.org/pep-0723/) 형식이라 의존성 설치 없이 바로 돈다.
**`pip` 은 쓰지 않는다.**

```bash
uv run tools/<이름>.py
```

| 파일 | 하는 일 |
|---|---|
| `import_room_assets.py` | 에셋 드롭 폴더 → `drawable-nodpi` 반입. 배경 뚫기·조각 털기·WebP 변환·리소스 이름 짓기를 한 번에 |
| `room_cutout.py` | 방 PNG 의 바깥 배경을 투명하게. 강아지 시트에서 몸과 떨어진 조각도 털어낸다 (위 스크립트가 부른다) |
| `trace_door.py` | 방 그림에서 문 윤곽을 떠서 `DoorSpec` 값을 뽑는다. 확인용 이미지도 같이 낸다 |
| `make_outside.py` | 창밖·문밖 풍경 12장(낮·밤 x 해·비·눈). 방 그림에서 유리를 오려 다시 칠한다. **씨앗이 고정이라 돌릴 때마다 같은 그림이 나온다** |
| `make_turntable.py` | 턴테이블 소품을 테마 6종으로 찍는다 |
| `isoasset.py` | 아이소메트릭 템플릿 생성, 스프라이트 각도 검사 |
| `convert_audio.py` | 배경음 변환 |

---

## 그림은 어디서 오나

방·소품·강아지 PNG 는 팀 다른 저장소에서 만든다.

- 저장소 **`frankie516c/dog-training-rag`**
- 브랜치: 테마는 **`feature/pastel-room-themes`**, 견종은 **`main`** 에서 받는다
- 경로 `ui-experiments/main-screen/assets/`
  - `themes/<테마>/*.png` — 방·소품 (테마 6종)
  - `dogs/<견종>/walk.png` — 강아지 워크 시트 (견종 25종, 2328×568 / 4프레임)
  - `dogs/<견종>/portrait.png` — 프로필 얼굴 (256×256). 아바타가 쓴다
- 견종별 크기 표 `ui-experiments/main-screen/drafts/dog-presets.js`
  (`visualWidth`·`bodyRadius`·`speed` — **저쪽 격자는 16, 우리는 12** 라 환산한다.
  `miniroom/art/DogShapes.kt` 참고)

받을 때 주의: **GitHub contents API 는 1MB 넘는 파일에 빈 내용을 준다.** 방 PNG 가
2MB 라 그냥 받으면 0바이트로 온다.

```bash
gh api "repos/frankie516c/dog-training-rag/contents/<경로>?ref=<브랜치>" \
  -H "Accept: application/vnd.github.raw" > out.png
```

받은 것을 `reference-room.png` + `themes/<테마>/*.png` + `dogs/<견종>.png` 구조로
모아 `uv run tools/import_room_assets.py <드롭폴더>` 를 돌린다.

**우리가 직접 그린 것도 같은 문으로 들어간다.** 드롭 폴더에 `window/` · `door/` 가
있으면 창밖·문밖으로 반입한다. 방 그림(`reference-room.png`)이 없어도 되므로 소품만
넣을 때도 이걸 쓴다.

```
<드롭폴더>/window/day_clear.png   ->  R.drawable.window_day_clear
<드롭폴더>/door/night_snow.png    ->  R.drawable.door_night_snow
```

새 그림이 오면 **실기기에 올려 화면 크기에서** 확인한다. 강아지는 원본 프레임 582px 가
화면에서 77~134px 로 그려진다 — 원본 크기로만 보면 문제가 안 보인다.

---

## 테스트

```bash
./gradlew :app:testDebugUnitTest
```

단위 테스트 84개가 좌표 변환·배치·앞뒤 정렬·문 터치·견종 규격·창밖 매핑을 잡는다.
**그림이 예쁜지는 테스트가 못 잡는다** — 그건 실기기에서 본다.
