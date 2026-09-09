# 행동 핀 위치 추정기 — 2단위

`walk.pin.ActionPinEstimator`는 GPS가 불안정할 때 **행동을 누른 시각의 좌표**를 계산한다.
GPS 수집, 버튼, Room, worker, API, 지도, 점령/사진 인증에는 아직 연결하지 않았다.
이 PR을 머지해도 기존 `recordMoment`의 GPS 차단 동작은 바뀌지 않는다. 연결은 후속 4단위다.

계약 원본은 [DEV 1단위 고정 커밋](https://github.com/SAJOYO/DAENGS_dev/blob/cae3a4403948ee774ff4d78b6a4fb6e0cd71b850/docs/walk/action-pin-location-contract.md),
APP 연결 설계는 [APP 1단위 고정 커밋](https://github.com/SAJOYO/DAENGS_APP/blob/1b8ba349d181b5aa17cef73ddc1c3fc3766cf480/docs/action-pin-location-contract.md)이다.
두 문서가 아직 dev에 병합되지 않아 고정 링크를 사용한다.

## 입력과 생명주기

- `ActionPinRequest`: 탭 때 고정한 resolution UUID, owner/session/chain, targetAtMillis.
- `ActionPinObservation`: 영속 원본 fix의 값 복사. 같은 walk의 clientSeq/chain/관측 시각을 유지한다.
  `LocationSample`이나 화면 경로를 임의의 raw ID로 바꾸지 않는다.
- `begin(request, observations, directFix?)`: 즉시 저장할 최초 결과. 호출이 늦어도 탭 이후 관측은 보지 않는다.
- `finish(initial, observations, computedAtMillis, reason, observationCutoffMillis)`: 저장된 결과와 원본을
  다시 넣어 한 번 확정한다. 계산 시각과 실제 관측 수집 종료 시각을 분리한다.

정상 경로는 기존 선택기가 정상으로 판단한 **실제 원본 참조**를 `directFix`로 전달한다.
현재 chain, 탭 이전 10초 이내, 유한 양수 accuracy≤15m, 정상 좌표, non-mock인지 방어 검증한 뒤
좌표를 그대로 `resolved/observed`로 반환한다. 원본 accuracy는 `provider_accuracy`로 보존한다.
이 선택기를 교체하거나 `latestMomentFix`에 값을 쓰지 않는다. `directFix=null`이면 좋은 관측이 있어도
fallback으로 계산하며 `observed`로 자동 승격하지 않는다.

fallback 최초 결과는 `provisional`이다. 근거가 있으면 estimated 또는 last_known, 없으면 null/none이다.
후속 수신을 최대 8초 기다릴 기한을 탭 시각 기준으로 고정한다. 대기를 실행하는 코드는 이 모듈에 없다.
초기 계산이 이미 늦었으면 호출자가 곧바로 보존 근거를 넣어 마감한다.

| 종료 호출 | 동작 |
|---|---|
| REFINED | 후속 관측을 포함한 지원 가능한 모델이 있으면 조기 확정. 없으면 최초 결과 그대로 반환 |
| DEADLINE | 기한 이후에만 허용. 새 근거로 추정할 수 없으면 최초 좌표를 그대로 유지 |
| SESSION_ENDED | 더 기다리지 않고 종료 당시까지 받은 근거로 마감 |
| RECOVERED | 재시작 때 저장된 기한/수집 종료 시각 안의 관측으로 마감. 기한 연장 없음 |
| ESTIMATOR_FAILED | 새 계산 없이 최초 좌표/참조를 그대로 유지하며 마감 |

최초 좌표가 있을 때 고립된 후속 좌표 하나가 그것을 밀어내지 않는다. 최초 좌표가 전혀 없고 후속
좌표 하나만 있으면, 이동을 알 수 없어 그 점에 머물렀다는 estimated/unknown으로 마감한다.
관측 시각을 행동 시각으로 바꾸거나 future fix를 observed/last_known으로 위장하지 않는다.
끝까지 쓸 수 있는 좌표가 없으면 unlocated/none이다. reason은 deadline 등 실제 마감 원인을 남긴다.

계산기는 상태 없는 함수다. terminal 결과를 다시 넘기면 같은 객체를 반환하지만, 과거 provisional을
재사용한 여러 호출을 막는 저장소는 아니다. **동일 액션·resolution·owner·revision 확인과 CAS,
최초 액션의 원자적 저장, 삭제/계정 전환 취소, 복구 및 monotonic 대기는 4단위의 의무**다.
시각 역행/잘못된 호출 reason 등 프로그래밍 오류는 예외로 알리며, 호출자는 이 오류로 액션을 삭제하면 안 된다.
출력 클래스는 내부 계산 모델이다. wire enum은 소문자 매핑이 필요하고 v2 JSON/Room 스키마 자체가 아니다.

## 계산 순서

1. owner/session을 먼저 제한하고, 같은 clientSeq의 동일 재수신은 합친다. 서로 다른 값으로 충돌한
   raw identity는 모두 제외한다. 음수 식별자/시각, 잘못된 위경도/비유한수, mock은 좌표 근거로 쓰지 않는다.
   accuracy가 없거나 잘못됐어도 위경도가 유효하면 최후 last_known 근거는 남길 수 있다.
2. 현재 chain의 탭 전 30초~탭 후 8초를 모델 후보로 삼는다. 정확도≤50m 후보 중 UTC 초당 한 점을
   선택해 과도하게 자주 수신한 구간이 표 수로 이기지 않게 한다. 동일 초에서는 accuracy, 탭과의 거리,
   clientSeq 순으로 고른다. 계산/종료/기한 시각 중 가장 이른 시각 이후 자료는 제외한다.
3. 정확도≤15m 후보로 만들 수 있는 연속성이 있는 경로가 3점 이상이면 그것을 우선한다. 부족하면
   ≤50m 후보로 넓힌다. 시간 순 후보 사이의 이동 거리와 속도·잡음 여유를 비교하고, 가장 많은
   관측이 연결되는 부분 경로를 동적 계획법으로 고른다. 동점은 accuracy 합, 최근 종료 시각 순이다.
4. 앞뒤 간격이 짧은 구간에서 선형 보간과 크게 어긋난 중간 점은 잔차가 가장 큰 것부터 제거한다.
   이상치 하나 때문에 이웃까지 동시에 버리지 않는다. 모델은 최소 3점의 지지를 요구한다.
5. 탭 주변의 짧은 구간이 저속이고 잔차가 작으면 정확도 가중 선형 회귀의 탭 시각 절편을 쓴다.
   이동 중에는 가장 가까운 전후 점 사이를 보간한다. 전체 산책을 직선으로 맞춰 회전을 지우지 않는다.
6. 한쪽 관측만 있으면 가까운 짧은 구간의 속도·잔차·예측 시간·이동 거리를 확인해 앞으로 또는 뒤로
   예측한다. 성립하지 않으면 걸러진 경로의 마지막 과거 좌표, 그것도 없으면 실제 과거 좌표를 그대로 쓴다.
   이전 chain의 좌표는 last_known으로만 쓸 수 있고 chain 경계를 가로질러 보간하지 않는다.

보간·회귀는 지구 중심 3차원 좌표로 계산하고 지표로 정규화한다. 날짜변경선에서 경도가 뒤집히거나
극점에서 cos(latitude)로 나누는 문제를 피한다. sourceRefs에는 채택 경로를 지지한 원본을
at/clientSeq 순서로 남긴다. last_known과 observed는 복사한 실제 한 점의 참조만 남긴다.
원본 배열을 변경하거나 추정 좌표를 raw fix로 추가하지 않는다.

## 버전과 초기 파라미터

`policyVersion=action-pin-policy-v1`, `algorithmVersion=action-pin-local-v1`.
현재 구현은 이 버전 하나다. 값을 바꿀 때는 새 버전과 이전 provisional 작업의 종료 호환을 함께 설계한다.
아래 값은 **합성 시험용 초기 정책**이며 실기기에서 교정한 보증값이 아니다.

| 항목 | 값 |
|---|---|
| 후속 관측 대기 / 과거 모델 구간 | 8초 / 30초 |
| 정상 좌표 | accuracy≤15m, 나이≤10초 |
| 모델 accuracy | 우선≤15m, 부족할 때≤50m |
| 모델 샘플링 / 최소 지지 | UTC 초당 1점 / 3점 |
| 연결 속도 / 잡음 여유 | 3m/s / 두 accuracy 합을 6~15m로 제한 |
| 고립 점 제거 | 앞뒤 총 8초 이내, 보간 잔차>6m, 최소 3점 유지 |
| 전후 보간 간격 | 합계 최대 20초 |
| 회귀 구간 / 최소 시간 폭 | anchor에서 8초 / 3초 |
| 회귀 가중치 | 1 / max(accuracy, 3m)² |
| 단방향 예측 | 최대 5초, anchor에서 10m, 속도≤3m/s, 최대 잔차≤6m |
| 저속 회귀 | 속도≤0.35m/s, 최대 잔차≤6m, 최근 근거까지≤5초 |

모델 후보는 최대 39개여서 경로 선택/고립 점 제거는 작은 O(n²) 계산이다. 입력 정규화는 전달된
원본 수에 비례해 메모리를 사용하고 정렬에 O(N log N)이 든다. 연결 시 매 탭에 전체 DB를 복사하지 말고
필요 구간과 마지막 과거 fix를 조회하되, 재현에 쓴 원본 스냅샷은 보존한다. 산책당 행동 횟수 제한은 없다.

estimated/last_known의 uncertainty는 항상 null/unknown이다. 회귀 잔차가 작다고 실제 위치 오차가
작다는 뜻은 아니며 provider accuracy를 모델의 보증 반경으로 복사하지 않는다. 현장 인증에 사용할 수 없다.

## 검증 결과

실행 명령 (Windows에서는 gradlew.bat):

```shell
./gradlew :app:testDebugUnitTest --tests "com.daengs.app.walk.pin.ActionPinEstimatorTest" --tests "com.daengs.app.walk.pin.ActionPinReplayTest"
```

31개 JUnit 테스트 통과. 선택 테스트 실행에 필요한 앱·테스트 컴파일도 통과했다. 전체 테스트,
APK 설치, 실기기 산책은 실행하지 않았다. 라이브러리/빌드 설정은 변경하지 않았다.

재생은 각 100개 고정 시드, 좌표 축마다 ±1m 잡음, 탭 순간 약 361m 점프를 넣은 합성 자료다.
비교 기준은 점프 전 마지막 정상 좌표(-2초) 유지다. 숫자는 미터이며 실제 GPS 정확도 주장이 아니다.

| 합성 경로 | 마지막 정상 좌표 평균 | 최초 추정 평균 | 최종 추정 평균 | 최종 P95 | 최종 최대 |
|---|---:|---:|---:|---:|---:|
| 정지 | 0.753 | 0.857 | 0.271 | 0.500 | 0.588 |
| 직진 1.5m/s | 3.037 | 0.857 | 0.525 | 0.963 | 1.264 |
| 탭 시점 직각 회전 | 2.067 | 0.857 | 1.658 | 2.254 | 2.528 |
| 탭 시점 정지 | 3.037 | 0.857 | 1.546 | 2.182 | 2.424 |

회전/정지는 정확한 전환 시각을 GPS만으로 알 수 없어 **최종값이 최초값보다 나빠질 수도 있다**.
REFINED는 후속 근거를 사용했다는 의미이며 정답 오차 감소의 증명이 아니다. 한 번만 확정하는 이유도 같다.
30m의 지속적인 공통 방향 편향은 별도 시험에서 그대로 30m 남았다. 지도 맞춤/IMU/독립 근거 없이
이를 식별하지 못한다. 급격한 왕복 이동, 다수의 일관된 오관측, 차량 이동도 별도 현장 평가가 필요하다.

기타 회귀: 10m 단발 점프, 낮은 accuracy 값으로 위장된 300m 점프, 탭 순간 저품질 점의 우선순위,
GPS 단절, 좌표 0개, 미래 표본만 존재, 경계 시각, 세션 종료/재시작/실패, owner/session/chain 분리,
중복·충돌, 잘못된 수치, 동시 시각 표본, 21개 독립 탭, 읽기 전용 결과, 날짜변경선/극점.

## 후속 연결

3단위 서버 v2 저장·검증 후, 4단위에서 최초 액션 저장/worker/복구/지도/동기화를 연결한다.
그때 기존 정상 선택기와 raw clientSeq를 대응시키고 content.location 원본을 별도로 보존한다.
현재 인증용 latestMomentFix, 위치 정확도 판정, 원본 경로/점령/사진 입력을 추정값으로 대체하지 않는다.
실제 산책 재생 자료로 파라미터와 기기별 편향을 검증하기 전 v2 쓰기나 추정 기능을 자동 활성화하지 않는다.
