# 문서

| 파일 | 내용 |
| --- | --- |
| [walk-measurement-scenes.md](walk-measurement-scenes.md) | 3단계: 측정 원본 주소·장면 revision·일괄 갱신과 지도 선택 |
| [walk-stored-measurement.md](walk-stored-measurement.md) | 일반 상세의 측정 검증·원자적 캐시·재열람과 1~2단계 적용 범위 |
| [walk-space-materials.md](walk-space-materials.md) | DEV 정규화 공간 재료 표시와 서버 일기 수신·Room 저장·재진입 검증 |
| [산책 장면·일기 기획](https://github.com/rkbuhtig/DAENGS_geo/blob/2345c5b3d2b24f5754f55b802aab59905b807eb9/docs/explorations/walk/diary/plan.md) · [GEO #266](https://github.com/rkbuhtig/DAENGS_geo/pull/266) | 개별 장면 우선·별도 전체 일기 생성의 기획 기준. 아래 구현 문서와 적용 상태를 구별 |
| [장면 서술 기획 경위·인계](https://github.com/rkbuhtig/DAENGS_geo/blob/2345c5b3d2b24f5754f55b802aab59905b807eb9/docs/explorations/walk/diary/narrative-design/README.md) | 대화 전개·장면/행동 서술·특별한 순간·슬롯/API·실험 실패·실제 출력 40개·다음 작업. 최신 기획 채택과 구현 미반영을 구별 |
| [gps-motion-policy-architecture.md](gps-motion-policy-architecture.md) | GPS 정책층 1차 설계 — 단계별 수집·저장·판정·표시·복구 계약 |
| [gps-recording-delivery.md](gps-recording-delivery.md) | GPS 2차 — 원본 저널과 종료 drain, Room·실기기 검증 |
| [gps-motion-engine.md](gps-motion-engine.md) | GPS 3차 — 동결 정책·이동 추정·구간 장벽·동일 저널 재생 (운영 미연결) |
| [collaboration.md](collaboration.md) | 협업 규칙 — 우선순위 · Iteration · PR 기준 · 데일리 · 회고 |
| [facility-orchestration-release.md](facility-orchestration-release.md) | 시설 v2 출시 활성화·공통 채팅의 공유 검색·찜 완료·복구와 검증 |
| [co-care-contract.md](co-care-contract.md) | 공동 돌봄 앱 계약·미니룸 정책 제안·서버 계약 검토·화면 연결안 (#261 / DEV #388) |
| [기능별 테스트 실행 지도](../app/src/test/README.md) | 기능별 Gradle 선택자, Room·동기화 검증 경계, 공용 helper 제공자·소비자 |
| [walk-photo-sync.md](walk-photo-sync.md) | 산책 사진 메타데이터 전송 — Room 13, 응답 유실/편집 복구, 서버 기능 협상 |
| [walk-upload-receipts.md](walk-upload-receipts.md) | 산책 청크 수신 확인 — 구형 응답 호환, 재전송·Room 상태·GPS 확인·봉인 순서 |
| [walk-diary-base-board.md](walk-diary-base-board.md) | 기본 보드의 같은 카드·본문·편집기 연결, 실제 GPS 앵커와 기존 형식 보존 |
| [walk-diary-generation.md](walk-diary-generation.md) | 서버 일기 생성·조회, 배경과 원본 분리, 사진·지도 연결 |
| [walk-behavior-comparison.md](walk-behavior-comparison.md) | 행동 기록 돌아보기 — 전체 산책과 행동 기록 산책의 지도 비교·근거·서버 연결 |
| [asset-workflow.md](asset-workflow.md) | 미니룸 에셋을 **직접 그릴 때**의 격자 · 카메라 각도 · 기준점 · WebP 변환 |
| `templates/` | 아이소메트릭 도안 (1x1 · 2x1 · 2x2, PNG + SVG). `uv run tools/isoasset.py template` 로 다시 만들 수 있다 |

돌리는 방법은 루트 [README.md](../README.md), 코드 규칙은 [CLAUDE.md](../CLAUDE.md),
되돌리기 번거로운 결정은 [HISTORY.md](../HISTORY.md) 에 있다.

## 여기 없는 것

- **결정 기록(`decisions.md`)을 따로 두지 않는다.** 백엔드 저장소
  (`SAJOYO/DAENGS_dev`) 에는 `docs/decisions.md` 가 D-001 부터 번호로 쌓여 있지만,
  이 저장소는 [HISTORY.md](../HISTORY.md) 가 같은 일을 하고 있다. 결정과 그 이유가
  이미 절마다 서사로 들어가 있어서, 번호를 새로 붙이면 두 군데가 된다.

- **CI 워크플로가 없다.** 협업 규칙 2절대로 CI/CD 는 P0 이 아니다. 로컬 검증 범위는
  [기능별 테스트 실행 지도](../app/src/test/README.md)에서 선택한다. APK 빌드·실기기 검증은
  변경한 기능의 실행 경계에 맞춰 추가한다.
