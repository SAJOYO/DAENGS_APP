# 행동 핀 위치 추정 — APP 연결 계약

상태: 1단위 명세 초안. 버튼·Kotlin·Room·API 동작은 아직 바뀌지 않았다.
작성/정적 대조: 2026-09-09, APP dev `7cec8a6`, DEV dev `b041ce4`.
카드: [APP #227](https://github.com/SAJOYO/DAENGS_APP/pull/227),
[공통 계약 DEV #354](https://github.com/SAJOYO/DAENGS_dev/pull/354).

## 공통 계약 원본

필드·상태·API·호환 규칙의 원본은 DEV
[행동 즉시 기록과 핀 위치 추정 계약](https://github.com/SAJOYO/DAENGS_dev/blob/cae3a4403948ee774ff4d78b6a4fb6e0cd71b850/docs/walk/action-pin-location-contract.md)과
[요청·응답 예시](https://github.com/SAJOYO/DAENGS_dev/blob/cae3a4403948ee774ff4d78b6a4fb6e0cd71b850/docs/walk/action-pin-location-contract.examples.json)다.
이쪽에 같은 wire 명세를 복제하지 않는다. 서버 카드의 병합 상태를 이 문서에 옮겨 적지 않는다.
현재 제품의 v1 동작은 [산책 기록](walk-records.md)에서 읽는다.

## 사용자 동작

킁킁·배설·짖기를 누르면 먼저 액션을 로컬에 보존한다. GPS 때문에 행동 버튼을 막지 않는다.
양호한 GPS는 기존 위치로 즉시 표시·확정하고, 불안정한 경우만 액션 주변 구간을 추가 계산한다.
이동 중에도 fallback을 적용한다. 정지 여부/엄격한 GPS 문턱을 다시 입력 가능 조건으로 쓰지 않는다.

좌표 근거가 있으면 현재 최선의 좌표에 잠정 핀을 표시하고, 후속 관측을 이용해 누른 시각의 좌표를
한 번 확정한다. 후속 관측이 없거나 추정기가 실패해도 핀을 제거하지 않고 가진 최선 위치로 마감한다.
관측 근거가 전혀 없으면 액션을 시간순 목록에 유지하고 지도 중심 등의 가짜 좌표를 사용하지 않는다.

## 기존 코드 연결 지점

| 경계 | 현재 | 후속 구현 의무 |
|---|---|---|
| [WalkScreen](../app/src/main/java/com/daengs/app/ui/walk/WalkScreen.kt) | momentEnabled가 최근 양호 fix에 의존 | 산책 세션/저장 가능 여부로 입력을 판단. GPS 품질은 좌표 선택 책임으로 이동 |
| [WalkTrackingService](../app/src/main/java/com/daengs/app/walk/WalkTrackingService.kt) | recordMoment가 fix 부재/노후 시 저장 전 반환 | 액션 먼저 원자 저장. 정상 경로/추정 작업 분기. 늦은 응답은 session/owner/resolution 대조 |
| [WalkEntry](../app/src/main/java/com/daengs/app/walk/WalkEntry.kt) | nullable point지만 behavior 검증에서 필수 | v1/v2 읽기 분리. 원본 관측과 표시 pin을 구분. 누른 시각 불변 |
| [WalkEntryStore](../app/src/main/java/com/daengs/app/walk/store/WalkEntryStore.kt) | payload/revision/dirty 보존 | 원본+pin+복구 가능한 작업의 한 트랜잭션 저장, 원본 정정과 추정 갱신의 CAS |
| [RoomWalkFixLog](../app/src/main/java/com/daengs/app/walk/store/RoomWalkFixLog.kt) | action 복원에서 point 없는 행 제외 | 지도 대상 필터와 전체 원본/집계를 분리. GPS 없는 행동을 잃지 않음 |
| [WalkEntrySync](../app/src/main/java/com/daengs/app/walk/sync/WalkEntrySync.kt) | v1 CRUD·409·ACK | v2 capability/표현·확정 PUT·삭제 410·업데이트 필요 426 처리. 오래된 ACK로 최신 로컬 pin을 덮지 않음 |
| [WalkSync](../app/src/main/java/com/daengs/app/walk/sync/WalkSync.kt) | GPS upload/finalize 뒤 entry sync | 추정 원본 참조가 업로드된 뒤 전송. 좌표 0개 산책도 기록 보존/동기화 확인 |
| [WalkRoute](../app/src/main/java/com/daengs/app/ui/walk/WalkRoute.kt) | Room 기록을 목록/지도에 투영 | 전체 액션 목록과 좌표 있는 핀을 구분. 위치 확정에도 선택 ID와 편집창 유지 |
| [WalkEntryEditor](../app/src/main/java/com/daengs/app/ui/walk/WalkEntryEditor.kt) | 위치 없음을 '메모'라고 표시 | 위치 없는 행동도 정확히 안내. 이번 계약에 수동 위치 편집은 추가하지 않음 |
| [WalkRecordProfile](../app/src/main/java/com/daengs/app/walk/diary/WalkRecordProfile.kt) | 근거를 기존 WalkEntry로 파싱 | v2 근거의 위치 없음/추정 상태 파싱, 행동 횟수와 위치 확정 독립 |

## 위치 추정기와 상태 수명

2단위의 추정기는 Android UI/네트워크/DB와 분리된 계산 모듈이다. target_at, 같은 산책의 원본
표본 이력, 정책 버전을 입력으로 받고 좌표/근거 참조/불확실성/종료 이유를 반환한다.
정상 fix 판정도 그 경계에서 명시한다. 현재 경로 필터가 행동 핀의 전처리인 것으로 가정하지 않는다.

버튼 한 번당 ID와 resolution_id를 생성한다. 연속 탭은 별개 액션이고 '약 열 번'을 강제 상한으로
사용하지 않는다. 계산 입력은 공유해도 액션을 합치지 않는다. 최초 원본 쓰기는 추정 계산을 기다리지
않는다. 즉시 위치 계산에 실패하면 보유한 유효 표본 좌표를 잠정값으로 쓰거나, 전혀 없으면 null을 쓴다.

- 원본과 잠정 pin, 추정 작업, 기한, 정책, 필요한 source refs를 한 번에 내구 저장한다.
- 사용자가 누른 시각과 관측/계산 시각을 각각 보존한다. '다음 GPS가 온 시각'을 행동 시각으로 쓰지 않는다.
- 후속 수집 대기는 실행 중 monotonic clock, 재시작 복구는 저장한 기한/세션 상태로 관리한다.
  재부팅·시계 역행 때 과거 작업을 새 대기 시간으로 무한 연장하지 않는다.
- 저장된 원본의 client_seq/chain_index/at를 사용한다. 추정 좌표를 raw fix로 추가하지 않는다.
- 산책 종료 때 수집을 끝내고 가진 근거로 마감한다. 종료 뒤 기기 위치 권한·foreground service를
  억지로 연장하지 않는다. 일시정지/재개 경계를 가로지르는 이동 보간은 금지한다.
- 프로세스가 죽었다면 보존 입력에서 재개 또는 기한 마감한다. 재시작 때 새 액션/새 resolution을 만들지 않는다.
- 계정 전환은 이전 작업의 결과 게시/전송을 막는다. 삭제는 추정 작업을 취소하고 원본/좌표와 함께 정리한다.
- 서버 최초 생성 전에 최종 위치가 나왔으면 최종값으로 생성 가능하다. 업로드 중 확정된 결과는
  최초 생성 ACK 뒤 확정 PUT으로 보낸다. 서버 revision과 로컬 변경 식별자를 섞지 않는다.
- DB 쓰기가 실패하면 저장 성공 안내를 내지 않는다. 추정만 실패하면 액션 저장은 성공한 상태로 유지한다.

## 인증과 표시

`latestMomentFix`는 점령/사진 등 다른 소비자가 사용한다. 추정 결과로 교체하지 않는다.
추정 pin은 액션 소유 데이터이며 [현장 접근 상태](territory-proximity.md)의 실측 증거가 아니다.
지도·상세는 provisional/resolved를 표현할 수 있어야 하지만 resolved를 'GPS 인증됨'이라고 표시하지 않는다.

지도 핀은 같은 액션 ID를 유지하고 위치 확정으로 새 핀을 만들지 않는다. 위치 없는 액션도 기록 수와
시간순 목록에 남긴다. 최선의 좌표가 오래된 경우 추정임을 확인할 수 있게 하며 최신 위치로 위장하지 않는다.

## 배포 순서와 후속 확인

v2 읽기 지원을 먼저 배포하고 쓰기는 서버 capability 활성화 뒤에 연다. 기존 APP은 위치 없는
behavior를 파싱하지 못하므로 nullable을 기존 v1 응답에 섞는 방식은 금지한다. v1 범위에 새 v2
기록이 있으면 서버는 426을 반환한다. 구버전 기기가 저절로 호환되는 것은 아니므로 업데이트 안내와
지원 버전 전환은 v2 쓰기 활성화의 선행 조건이다. 공통 계약의 rollback/410/409 규칙을 따른다.

후속 확인 항목:

- 정상 GPS와 fallback 각각 즉시 저장/표시; 이동 중 급회전·단발 점프·긴 단절 재생.
- 최초 좌표 없음, 후속만 있음, 끝까지 없음; 짧은 산책 종료에도 액션 보존.
- 잠정 표시 뒤 종료/재시작/삭제/계정 전환/연속 탭/ACK 유실.
- 로컬 확정과 서버 content 정정 경합; 삭제된 액션에 지연 결과 도착.
- GPS 0개 업로드/finalize/entry sync; 기존 기기 복원/프로필/스토리보드 읽기.
- 점령/사진 인증의 기존 테스트에서 추정 pin이 입력으로 사용되지 않음 확인.

이번 문서 단위는 링크/예시/현재 코드 경계만 검증한다. Gradle/APK/실기기를 실행해 새 동작을
검증한 것으로 보고하지 않는다. 추정 수치·보간 방식·최소 앱 버전·Room migration은 후속 단위다.
