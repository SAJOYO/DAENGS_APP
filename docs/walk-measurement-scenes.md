# 저장 측정과 장면 연결 — 3단계

[APP #368](https://github.com/SAJOYO/DAENGS_APP/pull/368)의 [저장 측정 소비](walk-stored-measurement.md)에 이어, 일반 상세의 사진·행동·자동 장면을 검증된 측정의 원본 주소에 연결한다.

## 연결 기준

`WalkMeasurementContract`가 이미 검증한 각 보행 구간의 원본 참조와 로컬 엔진의 usable 관측 집합을 유지한다. 주소는 session/source epoch/clock epoch/clientSeq 또는 control kind다. 표시 좌표가 같은 재방문도 원본 주소가 다르면 다른 구간이다. 단순화로 표시점에서 빠진 usable 관측은 최종 보행 구간의 원본 범위에 연결한다. 제외된 관측을 주변 보행선에 붙이거나 거리를 다시 계산하지 않는다.

`MeasurementSceneReview`는 자동 장면의 observation 및 행동 핀의 source_refs를 원본 관측과 대조한다. 행동 원본이 삭제되거나, 참조·세션·시각·위치가 맞지 않으면 연결하지 않는다. 추정 행동의 근거가 여러 최종 구간에 걸치거나 사건 시각이 해당 구간 밖이면 위치만 남긴다. 원본 참조가 없는 사진·메모는 원본 시각이 유일하거나, 동일 epoch에서 wall/elapsed 시간이 일치하는 짧은 구간 하나에만 해당할 때 연결한다. 동일 시각의 다른 방문이 있으면 근접 좌표로 하나를 고르지 않는다.

binding은 `sourceStart/sourceEnd`, `locationSource`, `eventSource`, `sectionId`를 가진다. 시간으로 보간한 주소에는 `locationSource`가 없다. 시작/종료 사건의 제어 주소와 첫/마지막 관측 주소, 사건 시각과 위치 취득 시각을 구분한다. 이전 위치를 사용한 행동은 이전 위치임을 표시하고 현재 사건의 보행선을 강조하지 않는다.

검증된 측정은 `StoredWalkDetailData → WalkDiaryReader → assembleDiary → diaryWalk`에도 함께 전달한다. 장면 조립의 관측 해석은 해당 세션의 source/clock epoch·원본 번호·usable 여부를 대조한다. 시계 보정으로 원본 wall time이 기록 시작/종료 범위를 벗어나도 확인된 위치를 지우지 않는다. 측정이 없는 기존 읽기는 원래의 시간 범위 검사를 유지하며, 다른 소유자의 측정은 reader에서 거부한다.

## 갱신과 선택

`SceneBindingKey`는 소유자·세션·장면 ID, measurement ID/result digest, 사건 revision, 장면 revision, 연결 정책 버전을 포함한다. 사건과 편집 내용의 revision은 길이와 타입을 포함한 필드의 SHA-256이다. 본문 수정은 장면 revision을 바꾸며 사건의 원본 주소를 덮어쓰지 않는다. 로그인 generation은 상세의 account guard에서 별도로 검사한다.

`WalkDiaryReadView`는 같은 읽기의 원본 행동과 장면을 함께 연결한다. 측정이 바뀌면 이전 완성된 화면을 유지하고 새 경로·장면·binding이 모두 준비된 뒤 함께 채택한다. 중간 실패는 이전 완성본을 유지하고 오류를 알린다. 삭제·계정 변경과 늦게 끝난 작업은 기존 읽기 경계에서 차단한다.

장면 선택 시 현재 읽기의 revision을 다시 확인한다. 오래된 콜백이나 다른 측정의 binding은 선택/카메라 이동에 사용할 수 없다. 지도 마커 선택은 카메라를 움직이지 않고, 목록 선택은 확인된 장면 위치를 사용한다. 연결할 수 없는 장면도 내용은 읽을 수 있다. 현재 사진 원본의 세션·촬영 시각·위치가 확인되면, 보행 대응이 모호해도 목록 선택으로 사진 위치에 이동한다. 보행 강조는 비우고 지도 마커 선택과 오래된 revision의 이동 차단은 유지한다. 데이터 도착만으로 카메라나 서랍 높이를 바꾸지 않는다. 기존 세 단계 서랍·지도 레이아웃을 사용하며 생산 화면의 `WalkDiaryMapPreview`를 제공한다.

## 저장과 남은 단계

이번 단계는 DB/서버 스키마를 추가하지 않는다. 기존 Room 측정 캐시와 원본 장면/행동을 읽어 binding을 재구성한다. 재열람 후 같은 입력이면 같은 key와 연결을 얻는다. 탐색 커서·시간 slice의 영속 주소 복원, 관측 보조선·방향 표현은 이후 단계다. 운영/main 반영은 별도 합의한다.

## 검사

`MeasurementSceneReviewTest`, `MeasurementScenePinTest`는 재방문, 생략된 관측, 원본 행동 참조, 이전 위치, 시간 보정, 본문 수정과 불일치 거부를 확인한다. `WalkMeasurementReadViewTest`는 측정·장면의 지연/취소/실패·삭제·계정 변경과 revision 일치 후 채택을 확인한다. `DiaryMapNavigationTest`는 실제 화면이 사용하는 선택 메서드의 카메라 동작을 검사한다. `WalkDetailDataUiTest`는 실제 상세 Composable에서 측정 교체·재진입 후 선택 장면·사용자 본문·서랍 높이를 확인한다.

`WalkMeasurementTest`는 서버 공통 32개 wire 사례의 실제 채택 결과에서 보행 정점별 source binding을 확인하고, Room 캐시 및 일반 상세 재열람의 연결 결과를 대조한다. 네이티브 지도 SDK를 실기기에서 조작한 검증은 포함하지 않는다.

리뷰 수정 후 `assembleDebug` 성공, 선택한 검사 265개 중 264개 통과·실패 0개다. 기존 `PrivateRecordContextReplayTest` 1개는 비공개 A/B 원본 파일이 없어 skip했다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests '*MeasurementScene*' --tests '*WalkMeasurement*' --tests '*WalkDiary*' --tests '*WalkDetail*' --tests '*CompletedRouteReviewTest' --tests '*RecordContext*' --tests '*WalkReadingBaselineUiTest' --tests '*WalkRecordOverviewUiTest' --tests '*DesignLockTest' --tests '*StoredWalkDetailDataTest' --tests '*DiaryMapNavigationTest' --tests '*ActionPin*' --tests '*StoryboardObservation*' --tests '*LocalDiary*' --tests '*ServerDiary*'
```

리뷰 회귀: `WalkMeasurementTest`의 시간 보정 사례는 기존 walking 원본의 GPS/수신 wall time만 120초 이동하고, DEV `ccc0d14`의 fingerprint 계산과 `project()`로 응답을 재생성한 `walk-measurement-clock-correction-v1.json`을 사용한다. 실제 Kotlin 채택·Room 캐시·일기 조립·binding을 거치며 기존 읽기의 범위 검사와 다른 소유자/epoch·제외 원본 거부도 확인한다. `DiaryMapNavigationTest`는 반복 시각 사진의 목록 이동, 경로 강조 없음, 지도 선택 시 카메라 유지, 오래된 선택과 사진 원본 불일치 차단을 확인한다.
