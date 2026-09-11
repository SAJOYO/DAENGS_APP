# GPS 이동 정책 엔진 — 3차 구현 (#294)

상태: #294에서 순수 엔진·완료 저널 재생을 구현했고, #307에서 [운영 서비스의 속도 표시에 연결했다](gps-speed-runtime.md). #313에서 [세션별 정책 저장과 완료 기록 비교](gps-policy-persistence.md)를, #319에서 [새 산책의 거리·경로·완료 요약](gps-measurement-integration.md)을 연결했다. 이하 #294 당시 검증 기록과 현재 연결 범위를 구분한다.
설계는 [#282](gps-motion-policy-architecture.md), 원본과 종료 증거는 [#283](gps-recording-delivery.md)를 따른다.

## 계산의 주인과 입력

`walk/motion`의 `MotionPolicyEngine`은 한 소비자가 소유한다. Android·Room·네트워크·coroutine·실제 시각을 호출하지 않는다. 입력은 연속된 `journalSeq`와 `Begin`, `Observation`, `End`, `Loss`다. callback 배치 경계는 계산 입력이 아니다.

관측은 기존 `RecordedFix`를 그대로 받는다. `clientSeq == ingressSeq`를 확인하고 source/clock/chain을 바꾸지 않는다. 다른 세션, 역전·재사용한 접수 번호, 건너뛴 journal 번호는 계약 오류로 거절한다. 원본 접수 번호의 공백은 `INGRESS_GAP`과 `hasKnownLoss`로 남기고 거리 연결을 끊는다. 처리 관측 수에 누락 개수를 더하지 않는다.

`Begin`은 구독·활동 구간을 열고 `End`는 해당 구간의 마지막 접수 번호와 단조 cutoff를 전달한다. PAUSE 이후 새 구독은 새 source와 증가한 chain을 요구한다. 시계 영역이 바뀌면 이전 시각과 차이를 계산하지 않는다. `closedRecordingDurationNanos`는 닫힌 활동 구간의 시간 합이다. #319의 측정 세션은 이 시간을 실시간·완료 요약에도 사용한다.

완료 저널 어댑터 `replayRecordedMotion`은 #283의 명시적 STOP·drain 증거를 먼저 확인한다. 원본을 한 번 순회하며 개수·번호·source·clock·chain을 대조한다. 정렬, 번호 재작성, 미완료 꼬리의 임의 종료는 하지 않는다. 이전 정책은 `[시작, 종료)` 규칙이며, #319 측정 버전은 종료 번호 장벽 전에 접수된 동시각 점도 포함한다. 구간 밖·무효 시각의 관측은 거리에서 제외하며 수신이 종료 뒤인데 eligible인 완료 증거는 거절한다. 검증 도중 계약 위반이 발견되면 예외를 반환한다. `onStep`은 중간 결과이며, 호출자는 함수가 정상 반환하기 전 결과를 완료본으로 공개하면 안 된다.

## 동결 정책과 과거 기록

`MotionPolicies.freeze(sessionId, config)`는 `motion-v1`, 관측 스키마 15, 유효 설정 JSON과 SHA-256을 고정한다. 설정은 불변 값이며 새 설정 객체를 만들어도 실행 중인 정책은 바뀌지 않는다.

`StoredMotionPolicy`는 동결 값 계약이다. #313은 이 envelope를 최초 세션 생성과 함께 저장한다. `resolve`와 저장 문자열의 `resolveJson`은 다음을 구분한다.

- 정책 없음: `Legacy`. 호출자가 기존 reader를 선택한다.
- 지원 버전·스키마·설정·해시: `Supported`.
- 미래 버전, 다른 스키마, 해시 손상, 누락·추가·잘못된 설정: `Unsupported`. 현재 기본값으로 대체하지 않는다.

같은 설정의 저장·복원과 동일 입력의 step 결과까지 테스트했다. #313의 비교 조회는 소유자를 검사하고 저장된 정책을 사용한다. 과거 기록 전체를 새 규칙으로 다시 쓰거나 서버 정책과 동일하다고 주장하지 않는다. #319는 명시적인 측정 버전을 가진 새 산책만 거리·경로를 전환한다.

## 추정과 거리의 분리

`MotionEstimator`는 유효 좌표의 제한된 최근 창만 사용한다. `SegmentPolicy`는 별도의 거리 노이즈 기준점과 최근 유효점을 가진다. 따라서 지도에 넣을 정도로 움직이지 않았어도 최근 관측을 속도 추정에 쓸 수 있고, 작은 보폭은 노이즈 기준점을 넘을 때 누적된다.

- 위치의 유효성·정확도와 속도 근거의 품질은 독립이다. 저정확도/잘못된 좌표도 신뢰 가능한 기기 속도는 별도로 보고할 수 있다. mock 관측의 속도·거리는 사용하지 않는다.
- 유효한 기기 속도와 속도 정확도가 있으면 `DEVICE/TRUSTED`다. 속도 정확도가 없으면 `UNVERIFIED`로 구분한다. 좌표 창 근거가 생기면 두 값의 일관성을 확인한다.
- 기기 속도를 쓸 수 없으면 같은 구간의 좌표 창에서 변위·시간·오차를 확인한다. 흔들림 범위 안이면 UNKNOWN이며 측정된 0으로 바꾸지 않는다.
- 측정된 최신 속도와 두 위치 사이의 구간 속도는 별도다. 새 점의 속도가 0이어도 앞뒤 변위가 고속이면 구간을 제외한다.
- 고속 이후에는 연결 기준점을 버리고 낮은 속도의 재진입 근거를 기다린다. 다시 인정하는 첫 점의 거리는 0이며 이전 구간을 잇지 않는다. 교통수단을 확정하지 않는다.
- 짧은 저정확도 관측을 건너뛸 수 있지만, 유효 위치 사이의 긴 공백·점프·누락·구독/시계 변경은 장벽을 세운다. `chainIndex`를 고속 단절 횟수로 재작성하지 않는다.
- 측정 순서가 증가하는 늦은 배치는 경로에 사용할 수 있다. 시각 역행, 같은 측정의 재전달, 같은 시각의 다른 payload는 각각 별도로 제외하며 추정 기준점을 뒤로 돌리지 않는다.

`MotionEstimate`는 측정 근거다. 마지막 표시 숫자 유지·STALE·바늘 보간은 여기에 없다. 거리도 화면 속도를 적분하지 않는다.

## 실험 설정

이 값은 구현을 재현하기 위한 초기 설정이며 실기기에서 보정한 권장값이 아니다. 기존 코드와 목적이 같은 상한은 출발점으로 유지하고, 서로 다른 품질 문턱은 합치지 않았다.

| 설정 | 기본값 | 의미 |
|---|---:|---|
| windowSize / windowSeconds | 8 / 15초 | 좌표 추정 창의 개수·시간 한도 |
| minCoordinateSeconds | 2초 | 좌표 추정 최소 시간 간격 |
| maxPositionAccuracyM | 50m | 경로에 사용하는 수평 정확도 상한 |
| maxSpeedAccuracyMps | 2m/s | 기기 속도 정확도 상한 |
| speedAgreementMps | 1.5m/s | 속도 정확도 없는 기기 값과 좌표 근거 비교의 최소 허용 폭 |
| maxWalkingSpeedMps | 7m/s | 고속 의심 상한 |
| stationarySpeedMps | 0.25m/s | 근거 있는 속도의 정지 분류 상한 |
| minDistanceM / noiseRadiusFactor | 3m / 0.5 | 거리 누적 문턱: max(3m, 양 끝 오차 반경 합 × 0.5) |
| maxJumpM / maxGapSeconds | 200m / 20초 | 점프·장기 공백 장벽 |
| reentrySamples / reentrySeconds | 2 / 1.5초 | 고속 제외 뒤 새 기준점을 만들기 위한 낮은 속도 근거 |

기기 속도가 없고 좌표 변위도 불확실하면 재진입이 지연될 수 있다. 이 보수적인 제외의 비용은 실제 정지·보행 재생으로 평가해야 한다. 숫자 표시를 지속시키기 위해 거리 인정 문턱을 완화하지 않는다.

## 검증

2026-09-11, dev `f33d38d` 기준으로 실제 Gradle 앱 빌드와 83개 선택 테스트를 실행해 모두 통과했다.

- 신규 33개: 엔진 23, 정책 저장 계약 4, 완료 저널 재생 6.
- 기존 50개: Room 이관 12, 원본 저널 2, 종료 증거 3, 기존 경로 17, 요약 16.
- 고속→0→보행 반례, 구간 속도와 순간 속도의 충돌, 저정확도와 유효 속도, 노이즈·보폭 누적, 결측·NaN·mock·시각 역행·중복, 누락과 잘못된 세션, source/clock 전환, 반열린 활동 구간, 빈 STOP을 확인했다.
- 동일 저널을 개별/3/16/128개 배치로 처리했을 때 중간 step과 최종 결과가 같았다. 동결 설정 복원 뒤 재생 결과도 같았다.
- 1만 건의 합성 입력에서 누적 거리와 최대 관측 창 8개를 확인했다. 실제 CPU·배터리·지연의 성능 측정은 아니다.
- 14버전 fixture에서 빠졌던 사진 동기화 행을 보완하고 공개된 보드·공개 시각 보존을 검증했다. migration SQL·Room 스키마·기존 reader 변경은 없다.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests 'com.daengs.app.walk.motion.*' --tests com.daengs.app.walk.store.WalkMigrationTest --tests com.daengs.app.walk.store.RecordingJournalTest --tests com.daengs.app.walk.RecordingCompletionTest --tests com.daengs.app.walk.TrailRecorderTest --tests com.daengs.app.walk.WalkSummaryTest
```

4차의 표시 상태, 5-1의 서비스 연결, #313의 정책 저장에 이어 #319에서 거리·경로·완료를 연결했다.
종료 cutoff와 저널 순서를 확정하고 정책 소비자 완료까지 기다린다. `MotionLifecycle.STOPPED`
자체는 원본 저장 완료의 영수증이 아니다. 60초·50m, 기존 핀·전송, 실시간/완료 일치는 표적
테스트로 검증하며, 야외 정확도·지연 예산·서버 동일 재생은 별도 후속 검증이다.
