# GPS 정밀 백업·계산 대조 (#327)

APP #325의 기본 GPS 백업 다음 단계다. 서버 계약은
[DEV #457](https://github.com/SAJOYO/DAENGS_dev/pull/457)의 `gps-motion-precision-v1`을 따른다.
원래 Double 좌표의 소수점 6자리 아래 정보가 서버 raw 저장에서 사라지므로,
기본 백업의 완료 지문에 원본 비트만 추가로 연결한다. v1 행동 핀은 유지한다.

## 원본의 출처와 저장

Room **17→18**은 앱 업데이트 때 자동 이관한다. 별도 DB 설치나 팀원 설정은 필요 없다.
`walk_fix.latBits/lngBits/accuracyBits`에 원래 Double/Float 비트를 남기고 읽을 때 우선 사용한다.
새 로컬 산책을 최초로 열 때만 `coordinateOrigin=captured`를 부여한다.
원격 정밀 자료를 검증해서 복원한 산책은 `verified`, 옛 기록·반올림 복원은 null이다.
옛 자료의 Double 표현이 있다고 원본으로 간주하지 않는다. null 출처에서는 정밀 업로드를 하지 않는다.
진행 중이던 옛 산책도 출처를 추측하지 않으므로 그 산책은 정밀 검증 대상에서 제외된다.

## 완료와 재시도

`WalkMotionSync`가 기본 백업을 완료한 뒤 `WalkMotionPrecisionSync`를 실행한다.
이미 기본 백업이 완료됐어도 정밀 검증이 남으면 영속 대기 목록에 포함한다.
정밀 manifest와 지문을 고정하고 256개씩 보낸다. 받은 chunk는 건너뛰고 잃은 완료 응답은 재요청한다.
`walk_motion_precision`의 업로드 완료와 검증 완료 시각은 별개다.

서버 `motion-calculation`에서 세션·정책·기본 지문·정밀 지문·원본 좌표 기반 여부를 확인한 뒤
실제 Kotlin 엔진으로 다시 계산해 거리, 기록 시간, 연결 구간의 원본 번호와 제외 사유 수를 대조한다.
정수와 결정은 정확히 같아야 하고 거리만 abs 1e-7 또는 rel 1e-10 오차를 허용한다.
대조가 통과해야 `verifiedAtMillis`와 작은 영수증을 저장하고 대기 목록에서 빠진다.
대용량 경로 배열은 Room 한 행에 저장하지 않는다. 대조 실패/서버 미지원/응답 유실은 대기로 남긴다.
기존 화면의 거리나 핀을 서버 응답으로 덮어쓰지는 않는다.

## 복원과 계정 경계

기본 manifest·관측과 정밀 chunk를 모두 검증하고 계산까지 맞춘 다음
정책·epoch·좌표 원본 비트·영수증을 한 Room transaction에 설치한다.
기본 자료만 먼저 복원된 경우에도 다음 목록 동기화에서 완성된 정밀 자료를 발견하면 갱신한다.
이때 기존 정책/기본 지문이 맞는지 다시 확인하며 이미 known-origin인 원본은 덮어쓰지 않는다.
정밀 자료가 아직 collecting이면 복원을 완료 처리하지 않는다.

HTTP 전후와 Room 저장 전후 계정을 확인한다. 기존 탈퇴 보호 잠금을 공유하며,
요청 중 삭제된 로컬 산책을 재생성하지 않는다. 세션을 삭제하면 정밀 영수증도 CASCADE로 삭제된다.

## 검증·적용

- `WalkPrecisionSyncTest`: Python과 공유한 32개 정밀 좌표 fixture를 실제 Kotlin 엔진으로 검증,
  지문, 불일치 거부, 업로드/대조 분리, 재개방 재시도, 미지원 서버, 계정/삭제, 원자적 복원, signed zero 보존.
- 기존 `WalkMotionSyncTest`, `WalkMigrationTest`(17→18 포함), `RecordingJournalTest`,
  `WalkDaoTest`, `WalkSyncTest`, 실제 Service→Room의 `WalkSpeedServiceTest`를 해당 경계로 선택한다.
- SDK/JDK는 기존 앱 개발 환경을 사용한다. 서버·DB 계정·추가 서비스 없이 JVM 테스트가 돈다.
- 이 PR은 #325 위에 쌓여 있다. #325 반영 후 dev를 기준으로 전환한다.
- 서버의 새 SQL 적용과 API 배포 후 실계정의 새 산책 왕복 검증이 남아 있다. 이번 작업은 폰 설치를 하지 않는다.

서버 `device_result_verified=false`와 기존 capability `calculation_verified=false`는 유지한다.
폰에서 성공한 대조 영수증은 서버에 업로드하지 않으므로 서버가 성공했다고 주장할 근거는 없다.
