# 댕스(Daengs) — 안드로이드 앱

Kotlin + Jetpack Compose. 이 저장소는 **홈 화면과 미니룸**을 담당한다. 게임 엔진은 안 쓴다.
방·소품·강아지는 전부 픽셀 아트 PNG 이고, 팀 다른 저장소에서 만든 것을 반입해 쓴다.

이 문서는 **짧게 유지한다.** 폴더 구조·설치 방법·도구 설명은 `README.md` 에 이미 있고,
여기 옮겨 적으면 두 군데가 되어 어긋난다. 여기에는 **가리키는 것과 규칙만** 둔다.

| 문서 | 언제 보나 |
| --- | --- |
| [`README.md`](README.md) | **돌리는 방법.** 빌드·설치·폴더·`tools/`·그림 받는 법 |
| [`STATUS.md`](STATUS.md) | 지금 어디까지 됐나 · 다음 결정 · 알려진 문제 |
| [`HISTORY.md`](HISTORY.md) | 어떻게 여기까지 왔나. **되돌리기 번거로운 결정이 여기 있다** |
| [`CONTEXT.md`](CONTEXT.md) | 앱 전체 기획 (백엔드·인증·데이터 포함). 이 저장소 밖 이야기도 많다 |
| [`docs/collaboration.md`](docs/collaboration.md) | 협업 규칙. 우선순위 · Iteration · PR · 회고 |
| [`docs/map-style.md`](docs/map-style.md) | 지도 스타일. 콘솔에 넣은 색과 편집기 제약 |
| ⛔ [`docs/design-locks.md`](docs/design-locks.md) | **잠긴 디자인.** 사용자가 정한 화면 결정 — 에이전트가 바꾸지 않는다 |
| [`docs/asset-workflow.md`](docs/asset-workflow.md) | 에셋을 직접 그릴 때의 격자·각도·기준점 |

## 명령어

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest   # 빌드 + 단위 테스트
adb install -r app/build/outputs/apk/debug/app-debug.apk
uv run tools/import_room_assets.py <드롭폴더>          # 그림 반입
```

Windows 에서는 `gradlew.bat`. 자세한 건 [`README.md`](README.md).

## 규칙

- ⛔ **[`docs/design-locks.md`](docs/design-locks.md) 에 적힌 화면 결정은 바꾸지 않는다.**
  바꿔야 할 이유가 있으면 코드를 고치지 말고 PR 본문에 제안으로 적고 멈춘다.
  `DesignLockTest` 가 깨지면 테스트를 고치지 말고 변경을 되돌린다.
  Codex 는 `AGENTS.md` 로 같은 규칙에 닿는다.

- **브랜치는 `dev` 가 기본이다.** 작업 브랜치는 `dev` 에서 따고 PR 로 `dev` 에 머지한다.
  `main` 은 릴리즈 스냅샷이라 **작업하지 않는다.** default 를 `main` 으로 바꾸지 말 것.
  **PR 은 작업이 끝난 뒤가 아니라 시작할 때 draft 로 연다** — `docs/collaboration.md` 4절.

- **그림은 `uv run tools/import_room_assets.py` 를 거친다.** PNG 를 `drawable-nodpi` 에
  직접 넣지 않는다. 그 스크립트가 배경 뚫기 · 몸에서 떨어진 조각 털기 · WebP(q92) 변환 ·
  리소스 이름 짓기를 한다. PNG 그대로면 57MB, 거치면 7.5MB 다.

- **`pip` 을 쓰지 않는다.** 파이썬 도구는 PEP 723 인라인 의존성 + `uv run` 이다 (팀 규칙).

- **`adb install` 출력을 버리지 않는다.** 이 폰은 `adb` 인증이 수시로 풀려서 설치가
  조용히 실패한다. `>/dev/null` 로 버렸다가 **옛 APK 를 찍어놓고 새 그림이라고 보고한
  적이 있다.** 출력에 `Success` 가 뜨는지 직접 본다.

  그림을 바꿨으면 한 단계 더 간다 — 기기에서 APK 를 되뽑아 해시로 대조한다.

  ```bash
  adb shell pm path com.daengs.app          # base.apk 경로
  adb pull <경로> now.apk
  # now.apk 안의 res/drawable-nodpi-v4/<이름>.webp 와 저장소 파일의 md5 비교
  ```

  **APK 파일 크기는 근거가 못 된다.** 정렬 패딩이 같이 움직여서, 리소스 400KB 를 넣었는데
  파일은 6KB 만 커진 적이 있다.

- **폰은 사용자가 쓰는 물건이다.** 스크린샷을 찍기 전에 앱이 앞에 떠 있는지
  (`dumpsys window | grep mCurrentFocus`) 확인한다. 남의 대화창이 찍힌다.

- **그림은 화면 크기에서 본다.** 강아지 원본 프레임은 582px 인데 화면에서는 77~134px 로
  그려진다. 원본 크기로만 보면 문제가 안 보인다 — 눈이 3픽셀이 되면서 무너진 적이 있다
  ([`HISTORY.md`](HISTORY.md) 11절).

- **견종 규격 숫자의 원본은 저쪽이다.** `dog-presets.js` 의 `visualWidth` · `bodyRadius` ·
  `speed` 를 그대로 적어두고 읽을 때 환산한다 (저쪽 격자 16, 우리 12).
  `miniroom/art/DogShapes.kt` 참고. **견종마다 덩치가 다르다** — 하나로 통일하지 말 것.

- **커밋 메시지는 한글 서술형이고 접두사가 없다.** `feat:` 같은 걸 붙이지 않는다.
  제목에는 무엇이 어떻게 잘못돼 있었는지를 쓰고(`~던 것`), 본문에 왜 그렇게 고쳤는지와
  무엇을 재봤는지를 쓴다. **PR 제목만 `타입: 무엇을` 이다** — 둘은 다른 규칙이다.

- **테스트는 좌표·배치·규격을 잡는다. 그림이 예쁜지는 못 잡는다.** 그건 실기기에서 본다.
  Composable 을 새로 만들거나 고치면 `@Preview` 를 붙인다.

- `CONTEXT.md` 의 `### Claude Code가 지켜야 할 것` 에도 옛 규칙이 남아 있다.
  **충돌하면 이 문서가 최신이다.**
