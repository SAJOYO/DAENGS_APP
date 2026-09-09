# 문서

| 파일 | 내용 |
| --- | --- |
| [collaboration.md](collaboration.md) | 협업 규칙 — 우선순위 · Iteration · PR 기준 · 데일리 · 회고 |
| [walk-photo-sync.md](walk-photo-sync.md) | 산책 사진 메타데이터 전송 — Room 13, 응답 유실/편집 복구, 서버 기능 협상 |
| [asset-workflow.md](asset-workflow.md) | 미니룸 에셋을 **직접 그릴 때**의 격자 · 카메라 각도 · 기준점 · WebP 변환 |
| `templates/` | 아이소메트릭 도안 (1x1 · 2x1 · 2x2, PNG + SVG). `uv run tools/isoasset.py template` 로 다시 만들 수 있다 |

돌리는 방법은 루트 [README.md](../README.md), 코드 규칙은 [CLAUDE.md](../CLAUDE.md),
되돌리기 번거로운 결정은 [HISTORY.md](../HISTORY.md) 에 있다.

## 여기 없는 것

- **결정 기록(`decisions.md`)을 따로 두지 않는다.** 백엔드 저장소
  (`SAJOYO/DAENGS_dev`) 에는 `docs/decisions.md` 가 D-001 부터 번호로 쌓여 있지만,
  이 저장소는 [HISTORY.md](../HISTORY.md) 가 같은 일을 하고 있다. 결정과 그 이유가
  이미 절마다 서사로 들어가 있어서, 번호를 새로 붙이면 두 군데가 된다.

- **CI 워크플로가 없다.** 협업 규칙 2절대로 CI/CD 는 P0 이 아니다. 지금은 사람이
  `./gradlew :app:assembleDebug :app:testDebugUnitTest` 를 돌리고 실기기에서 본다.
