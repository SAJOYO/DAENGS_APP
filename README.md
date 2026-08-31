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

### 피부 진단을 켜려면 (선택)

**안 채워도 앱은 돌아간다.** 채팅의 사진 진단 버튼이 "진단 서버가 아직 없어요"라고
알려 줄 뿐이다. 실제로 써 보려면 한 줄을 더 넣는다.

```properties
daengs.screenUrl=http://daengback.weareithero.cloud/screen
```

**카카오 쪽 `daengs.apiBaseUrl` 과 같은 서버지만 다른 컨테이너다.** 모델은
`DAENGS_dev` 의 `skin-screening/` 에서 따로 돌고, nginx 가 `/screen/` 을 그쪽으로
넘긴다. 앱은 이 주소 뒤에 `/v1/screen` 을 붙인다.

살아 있는지는 이걸로 본다.

```bash
curl http://daengback.weareithero.cloud/screen/healthz
# {"ok":true,"mock":false,"contract_version":"1.0","threshold":0.1466...}
```

- `mock` 이 `true` 면 가짜 응답이다. 숫자를 믿으면 안 된다.
- **`threshold` 가 모델 판이다.** 재학습해서 갈아끼우면 이 값이 바뀌므로, 서버에
  새 가중치가 물렸는지 여기서 확인한다.
- 서버가 **꺼져 있을 수 있다.** 스크리닝은 `profiles: ["screening"]` 뒤에 있어서
  평소 `docker compose up -d` 로는 안 뜬다. 못 닿으면 앱이 주소를 화면에 띄워 준다.

#### 내 PC 에서 띄워 쓸 때

모델을 직접 고치는 중이라면 [`gayeoniee/deeplearning_test`](https://github.com/gayeoniee/deeplearning_test)
를 받아 돌리고 주소만 바꾼다.

```bash
uv run --extra train --extra serve python serve.py --release <release폴더> --host 0.0.0.0
```

- **`--host 0.0.0.0` 이 중요하다.** 기본값 `127.0.0.1` 은 그 PC 안에서만 보여서,
  같은 와이파이라도 폰이 못 닿는다.
- USB 로 이어 놨다면 `adb reverse` 로도 된다. 이 다리는 USB 를 뺐다 꽂거나 adb 가
  재시작하면 **말없이 사라진다** — 앱은 잘 떠 있는데 진단만 안 되면 여기부터 본다.

  ```bash
  adb reverse tcp:8000 tcp:8000   # daengs.screenUrl=http://127.0.0.1:8000
  adb reverse --list              # 걸려 있는지 확인
  ```

### 지도를 켜려면 (선택)

**안 채워도 앱은 켜진다.** 지도 타일만 인증 실패로 비고 나머지 화면은 그대로 돈다
(카카오 키와 같은 철학).

```properties
daengs.naverMapClientId=<네이버 클라우드 플랫폼의 Maps 클라이언트 ID>
```

네이버 클라우드 플랫폼 콘솔 → Maps → 인증 정보. 앱 패키지명 `com.daengs.app` 을
등록해야 그 키로 타일이 나온다.

- 지도 SDK 도 메이븐 센트럴에 없다. `settings.gradle.kts` 가 네이버 저장소를 따로
  열어 두었다 (카카오와 같은 이유).
- **산책기록 탭의 장소 검색은 이 키와 별개다** — 그쪽은 `daengs.apiBaseUrl` 의
  서버를 부른다.

### 빌드 · 테스트 · 설치

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 에서는 `gradlew.bat` 을 쓴다.

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
    miniroom/   미니룸 — 좌표계·배치·강아지·그리기
    ui/         홈 화면, 인벤토리, 개발자 패널
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
