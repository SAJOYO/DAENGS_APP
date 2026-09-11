# GPS 측정 백업·복원 연결 (#325)

#319에서 저장한 측정 정책과 원본 수신 자료를 DEV #449의 `gps-motion-backup-v1`에 연결한다.
서버 SQL은 2026-09-11 지정된 개발 DB에 적용·검증됐다. 다른 배포 환경의 지원 여부는 앱이
인증된 `/app/walks/motion-capabilities` 응답으로 확인한다. v2 핀 활성화와 서버 계산 전환은 별도다.

## 전송과 재시도

1. 기존 raw 업로드·eligibility 확인·finalize를 끝낸다. `derived`는 기존 서버 계산 완료 상태다.
2. `WalkMotionStore`가 소유권·완료 epoch·정책·원본을 같은 Room 트랜잭션에서 읽는다.
   지원하는 `motion-measurement-v1` 세션만 백업을 만들고, 미지원·손상 정책은 실패로 남긴다.
3. manifest와 전체 관측을 묶는 지문을 `walk_motion_backup`에 먼저 고정한다.
   서버가 미지원이거나 통신이 끊겨도 원본과 전송할 내용이 남는다.
4. 서버가 지원하면 manifest를 확정하고 이미 받은 청크 번호를 확인한다. 고정 크기 256개의
   누락 청크만 보내며 각 응답의 지문과 관측을 대조한다.
5. complete 응답의 manifest·전체 청크 범위·evidence 지문이 모두 맞아야 `completedAtMillis`를 저장한다.
   HTTP 200, raw의 `derived`, 기존 recording 영수증만으로 측정 백업을 완료 처리하지 않는다.

원본/정책이 고정 후 변경되면 자동 덮어쓰기하지 않는다. 서버의 manifest와 로컬 지문이
다르거나 완료 증명이 잘못되면 pending을 유지한다. 마지막 오류는 응답 본문 대신
`RETRY_PENDING` / `CONTRACT_REJECTED`로 저장해 토큰·개인정보가 새 상태에 들어가지 않게 한다.

관측은 기존 `walk_fix` 행에 보존하고 재시도 때 청크를 재구성한 뒤 고정 지문과 대조한다.
긴 산책 전체 JSON을 단일 SQLite 행에 넣어 CursorWindow 크기 제한에 걸리지 않게 한다.

완료되지 않은 측정 세션은 `sessionsPendingAnalysis()`의 재예약 후보에 포함한다.
원본이 이미 derived여도 앱 재시작의 `enqueuePending`이 찾는다. WorkManager는 네트워크 복귀·
일시 장애에 backoff로 재시도하고, 계약 오류는 무한 재시도하지 않는다. 다음 명시적 동기화나
앱 시작에서는 로컬 상태가 남아 있어 다시 확인할 수 있다.

기존 행동→사진→장면 동기화 순서는 유지한다. 측정 백업 오류는 이 경로를 막지 않고 별도로
처리한다. 기존 사진 업로드가 실패했을 때 장면 생성이 먼저 진행되지 않는 계약도 유지한다.
정책이 없는 기록과 #313의 속도 전용 정책은 새 백업 대상으로 승격하지 않는다.
이미 측정 백업이 완료된 세션은 GPS 전체를 다시 읽거나 서버로 재전송하지 않는다.

## 복원

`WalkSync.syncOnce`의 서버 목록 읽기에 연결한다. 새 기기에 없는 산책 또는 기존에 raw만
복원한 산책을 대상으로, 서버 raw와 완료된 측정 백업을 함께 읽는다.

- manifest의 세션·원본 지문·개수·정책·epoch를 검사한다.
- 모든 청크를 순서대로 받고 청크 지문과 완료 지문을 재계산한다. null, 정수, Float bits를 엄격히 해석한다.
- 같은 앱 측정 엔진으로 epoch와 관측 참조를 검사한다. 서버가 계산한 거리와 같다는 검사는 아니다.
- 전부 맞을 때만 세션·fix·epoch·정책·영수증을 한 Room 트랜잭션에 저장한다.

부분 다운로드나 `collecting` 백업은 로컬 완료 기록으로 저장하지 않고 다음 `syncOnce`에서
다시 받는다. 신규 원격 기록의 복원 재시도는 기존 목록 동기화에 속하며, 업로드용 WorkManager
대기열과 구분한다. 완전하지 않은 다운로드 상태를 유지하는 별도 임시 DB는 없다.

서버가 명시적으로 미지원이거나 해당 백업이 404면 raw만 legacy로 원자적으로 복원한다.
나중에 백업이 완성되면 다음 목록 동기화에서 검증해 측정 정책을 붙일 수 있다. 기존 로컬
정책·epoch가 있는 산책은 덮어쓰지 않고, 사용자 메모·핀·사진도 수정하지 않는다.
미래 capability·인증 실패·잘못된 지문·네트워크 오류는 legacy 성공으로 바꾸지 않는다.

계정은 HTTP 전후·Room 쓰기 전후에 확인하고, 실제 앱에서는 `RoomWalkFixLog`의 탈퇴 보호
잠금을 복원과 공유한다. 탈퇴 직후 늦은 응답으로 기록이 재생성되지 않는다. 이미 로컬에 있던
복원 대상이 요청 중 삭제돼도 재생성하지 않는다. epoch ID가 다른 산책과 충돌하면 전체 저장을 되돌린다.

## 로컬 DB와 원본 정밀도

Room **16→17**은 앱 업데이트 시 자동 적용한다. 사용자가 DB를 만들거나 설정할 필요는 없다.
새 백업 표는 세션 삭제에 따라 함께 지워지며, 기존 기록·정책·행동·사진·대기열을 보존한다.
기존 세션을 완료된 백업으로 채우지 않는다.

SQLite REAL은 NaN과 -0을 그대로 보존하지 않을 수 있어 `walk_fix`에 nullable Int bits 4개를
추가했다. 새 관측과 복원 자료의 speed/bearing·각 accuracy는 원래 Float 비트를 저장하고
읽을 때 우선 사용한다. 기존 행의 bits는 null로 남겨 이전 reader 값으로 읽는다.
이전 버전에서 이미 정규화된 Float 정보를 추측해서 복구하지 않는다.
기존 raw 좌표의 서버 저장 형식과 지문은 변경하지 않는다.

전송 시 시각은 Long JSON 정수, Float는 소문자 8자리 raw bits다. 서버 golden fixture를 그대로
`app/src/test/resources/walk/gps-motion-backup-v1.json`에 둬 manifest·청크·evidence의 공통
지문을 비교한다. 정책 설정 JSON은 최초 문자열 그대로 유지한다.

## 검증 범위

`WalkMotionSyncTest`는 공통 지문, 2^53 초과 시각, -0·NaN payload·null, 여러 청크의 중단·
완료 응답 유실·파일 DB 재개방, 복원의 전체 검증·SQLite rollback, 계정 전환·탈퇴·삭제,
legacy 승격 경계·미래 계약 거부·동시 호출·빈 기록·실제 WalkSync 연결을 확인한다.
기존 `WalkMigrationTest`에 16→17 보존 검증, `RecordingJournalTest`에 특수 Float 중복 수신을 추가했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.sync.WalkMotionSyncTest' --tests 'com.daengs.app.walk.store.WalkMigrationTest' --tests 'com.daengs.app.walk.store.RecordingJournalTest' --tests 'com.daengs.app.walk.sync.WalkSyncTest' --tests 'com.daengs.app.walk.sync.WalkRecordingSyncTest' --tests 'com.daengs.app.walk.sync.WalkDeliveryTest' --tests 'com.daengs.app.walk.WalkSpeedServiceTest'
```

실계정 서버 왕복·야외 GPS·다른 물리 기기 복원·배터리는 후속 실증 범위다.
서버의 동일 정책 재생 및 앱/서버 거리·시간 동등성 검증은 다음 구현 단위다.

2026-09-11 로컬 표적 검증에서 기존 사진 실패 후 장면 생성이 진행되는 회귀를 발견해
기존 순서로 수정했다. 수정 후 동기화·저널 38개, 최종 저장 구조 변경 후 백업·동기화·
마이그레이션 50개가 모두 통과했다. 최종 50개에는 6,000개 관측 전송과 고정 후 원본 변경
거부가 포함된다. debug APK 빌드도 통과했다. 전체 테스트와 유료 CI는 실행하지 않았다.
