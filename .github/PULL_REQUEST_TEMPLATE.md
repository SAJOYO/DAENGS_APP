<!--
이 PR 은 Project 의 카드 한 장입니다.
Iteration 은 3~4일이고, 그 안에서 이 PR 을 열고 끝나면 dev 로 머지합니다.
이슈는 쓰지 않습니다 — 이 PR 본문이 그 작업의 유일한 기록입니다.

담당자 / 상태 / 기간 / Iteration / Size / Priority 는 Project 필드에 있습니다.
여기에 다시 적지 마세요. 두 군데가 되면 반드시 어긋납니다.
이 본문에는 Project 필드가 담을 수 없는 것만 적습니다.

제목이 곧 보드의 카드 이름입니다: `타입: 무엇을` (예: `feat: 사용자용 견종 선택 화면`).
타입은 feat / fix / docs / refactor / build / chore.
**커밋 메시지는 이 규칙을 따르지 않습니다** — 한글 서술형에 접두사를 붙이지 않습니다.
자세한 건 docs/collaboration.md 4절.

Claude Code 에게:
- 작업 전에 `gh pr view --json title,body -q '.title, .body'` 로 이 본문을 먼저 읽으세요.
  (Project 필드까지 보려면 `gh auth refresh -h github.com -s project` 가 한 번 필요합니다.)
- 시작 전에 `CLAUDE.md` 를 읽으세요. 에셋·격자·검증 제약이 거기 있습니다.
- 아래 `##` 제목은 고정입니다. 제목은 두고 내용만 채우세요.
  `## 착수 절차` 는 카드를 여는 시점이 아니라 **실제로 시작할 때** 체크합니다.
  해당 없는 섹션은 `- 없음` 한 줄로 두고, 섹션 자체를 지우지는 마세요.
- 작업 중 본문이 낡으면 `gh pr edit --body-file <파일>` 로 갱신하세요.
  특히 `## 컨텍스트 메모` 는 다음 세션의 Claude 가 읽는 유일한 인수인계입니다.
- 되돌리기 번거로운 결정은 여기 말고 `HISTORY.md` 에 절을 하나 추가해 적으세요.
- 협업 규칙(우선순위 · Iteration · PR 기준 · 회고)은 `docs/collaboration.md` 에 있습니다.
-->

## 착수 절차

<!-- 카드를 여는 시점이 아니라 **실제로 시작할 때** 밟습니다. 며칠 뒤일 수 있습니다. -->

- [ ] Project 카드 Status → **In progress**
- [ ] **이 PR 이 쓰는 브랜치로 이동합니다. 새로 파지 마세요.**
      `gh pr view <번호> --json headRefName -q .headRefName`
      새 이름으로 파면 커밋이 거기 쌓이고 PR 은 **원래의 빈 브랜치를 머지**합니다 —
      에러가 안 나고 `dev` 에는 아무것도 안 들어갑니다 (백엔드 저장소 2026-08-30 `#68`)
- [ ] `git fetch team && git merge team/dev` — 열어 둔 사이 dev 가 움직였습니다
- [ ] 이 본문을 다시 읽습니다 — 열어 둔 사이 다른 카드가 전제를 바꿨을 수 있습니다

## 무엇을 / 왜

<!-- 2~4줄. 커밋 목록 말고 의도. "왜 지금 이게 필요했는지"가 빠지면 안 됩니다. -->

## 작업 목록

<!--
이 PR 안에서 쪼갠 단위. 영역 태그:
  UI     홈 화면 · 카드 · 도감 · 개발자 패널
  ROOM   미니룸 좌표 · 배치 · 앞뒤 정렬 · 강아지 움직임
  ART    그림 반입, 리소스, 견종·테마 규격
  TOOLS  tools/ 파이썬 도구
  DOCS   문서
-->

- [ ] `UI`
- [ ] `ROOM`

## 컨텍스트 메모

<!--
코드를 봐도 알 수 없는 것만. 다음 사람(과 다음 세션의 Claude)이 모르면 같은 실수를 할 것들.
예)
- 견종 크기 숫자의 원본은 저쪽 `dog-presets.js` 다. 저쪽 격자는 16, 우리는 12라 환산한다.
- 이 그림은 저쪽 PR #17 에서 받은 것. 베이지가 원본이고 나머지는 색만 바꾼 것이다.
- 강아지 목록은 기기에 저장하지 않는다. 서버가 진짜라 무엇을 바꾸든 목록을 다시 받아 온다.
-->

- 없음

## 에셋·리소스 영향

<!--
머지해도 배포되지 않습니다. 대신 사람 손이 더 가는 것들입니다. 해당하는 것만 체크.
-->

- [ ] 없음 — 코드만 바뀜
- [ ] `drawable-nodpi` 에 그림 추가/교체 → **실기기에 깔고 APK 를 되뽑아 해시로 확인**
- [ ] `DogBreed` 표 변경 → `DogBreedPresetTest` 도 같이 고쳤는지
- [ ] 테마 팩 추가 → `RoomTheme` 과 `ItemCatalog` 양쪽
- [ ] `gradle/libs.versions.toml` · `compileSdk` / `minSdk` 변경
- [ ] 저쪽 저장소에서 받아온 것 → **어느 저장소 · 어느 커밋인지** 아래에 적을 것

받아온 곳 / 필요한 조치:

## 확인한 것

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` 통과
- [ ] 새로 만들거나 고친 Composable 에 `@Preview` 를 붙였다
- [ ] 실기기에서 확인했다. `adb install` 출력이 `Success` 인 것을 **직접 봤다**
- [ ] 그림을 건드렸으면 **화면 크기에서** 봤다 (원본 크기로만 보면 문제가 안 보인다)
- [ ] 의존성은 `gradle/libs.versions.toml` 에 넣었다
- [ ] **릴리스 서명 키가 diff 에 없다** (`keystore/debug.keystore` 는 일부러 들어 있다)

## 남은 것

<!-- 못 끝냈거나 하다 보니 새로 생긴 일. 다음 Iteration 카드가 여기서 만들어집니다. -->

- 없음
