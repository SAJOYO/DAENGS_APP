# 일반 산책 상세의 저장 측정 소비

[DEV #495](https://github.com/SAJOYO/DAENGS_dev/pull/495)의 `walk-measurement-v1`을 [APP #368](https://github.com/SAJOYO/DAENGS_APP/pull/368)에서 소비한다. 대상은 원본 좌표와 정밀 백업 영수증이 있는 완료된 새 motion 측정이다. 운영/main 반영은 별도 합의로 진행한다.

## 실제 연결

`DaengsApp.walkMeasurements`를 `WalkMotionSync`의 정밀 백업 다음 단계와 `WalkSessionDetailRoute → StoredWalkDetailData`에 연결한다. 일반 상세를 열면 기존 로컬 결과를 읽고, 완료된 검증 캐시가 있으면 이를 채택한다. 화면 열기의 전송 예약과 측정 refresh, 종료 후 WorkManager 전송도 같은 소비자를 사용한다.

소비자는 입력 지문·소유자/로그인 generation·원본 좌표와 시각, 모든 청크의 순서/크기/해시, 로컬 엔진이 포함한 정확한 구간과 거리, 기록 시간과 시작/끝 경계를 검증한다. 모든 데이터가 준비된 후 Room 트랜잭션 하나로 요약과 페이지를 게시한다. `WalkDiaryReadView`가 거리·경로·경계와 탐색 인덱스를 같이 교체한다. 경로 누적 거리는 서버의 최종 구간 기여량을 더한다.

Room 18→19는 `walk_measurement`, `walk_measurement_chunk`를 추가한다. 원본·기존 백업·일기·사진은 보존한다. 캐시는 원본 세션에 FK로 연결되어 삭제/탈퇴 시 같이 지워진다. 한 행에 긴 경로 전체를 넣지 않는다. 재열람 때 캐시 바이트와 로컬 입력을 다시 확인하므로 DB를 닫고 다시 열거나 오프라인이어도 같은 측정을 읽는다.

서버 미지원, 다운로드 중단, 검증 실패, 캐시 손상 시 기존 로컬 상세를 유지한다. 일부 청크를 채택하지 않는다. 로그인 generation 변경이나 읽는 동안 삭제된 입력으로 결과를 게시하지 않는다. 생성된 원본이 없는 과거 legacy 산책에는 새 측정을 추측하여 만들지 않는다.

## 1~2단계의 UI 범위

기존 상세 지도·서랍·장면 편집·카메라 조작을 사용한다. 측정에서 확인된 기록 시작/종료와 첫/마지막 관측, 산책 경로 끝점을 함께 공급한다. 새 Composable이나 디자인 변경은 없다.

관측 run은 측정 메타데이터로 보관한다. 장면 binding의 measurement/event/scene revision과 원본 주소 연결은 이어진 [3단계](walk-measurement-scenes.md)에서 구현했다. 새 관측 보조선과 방향 표현, 시간 slice 및 재생 주소의 영속 복원은 이후 단계다. 기존 legacy 관측 판정기를 새 motion 입력에 적용하지 않는다. 새 측정의 재생 시간을 wall-clock 차이로 추측하지 않는다.

## 검증

`assembleDebug` 성공. 다음 선택자로 실행한 검사 181개 중 180개 통과, 기존 비공개 A/B 원본 파일을 요구하는 `PrivateRecordContextReplayTest` 1개는 원본이 없어 skip했다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*WalkMeasurement*' --tests '*WalkMigrationTest' --tests '*StoredWalkDetailDataTest' --tests '*WalkDiary*' --tests '*WalkPrecisionSyncTest' --tests '*RecordContext*' --tests '*WalkDetail*'
```

측정 테스트는 서버의 32개 정밀 원본 사례를 실제 Kotlin 소비자와 대조하고, 600점 경로의 청크 중간 분할과 DB 재개방, 일반 StoredWalkDetailData 재열람, 누락/변조/다른 입력의 거부, 계정 generation과 삭제 경계를 확인한다. 실제 기기에 새 APK를 설치하는 검증은 이번 범위에 포함하지 않았다.

최종 추가 확인에서 기존 데이터 보존 migration 16개와 디자인 잠금 7개가 모두 통과했다.
