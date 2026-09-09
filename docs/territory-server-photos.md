# 서버 인증 촬영·판정 — APP 연결 3단계

[APP #165](https://github.com/SAJOYO/DAENGS_APP/pull/165)는 [서버 세션·영역표시 #164](territory-server-actions.md)에
인증 촬영과 서버 판정 결과를 연결한다. 계약은 [Dev #260](https://github.com/SAJOYO/DAENGS_dev/pull/260)의
점유 API와 기존 `/app/territory/attempts`다. 운영 DB 적용이나 서버 배포는 실행하지 않는다.

## 켜지는 범위와 화면

```powershell
# 서버 준비 후 사용할 명시적인 테스트 빌드
./gradlew.bat :app:assembleDebug -PterritoryServerActions=true
```

현재 기본 debug와 release는 서버 점유 조회를 제공한다 (#221).
로컬 점령·페이크 판정은 debug에 `-PterritoryServerRead=false`를 명시한다.
서버 액션 모드에서만 새 사진 연결이 켜진다. release의 점령·촬영 액션은 계속 비활성이다.

- 산책 시작 전에는 점유만 읽는다. 대표견 선택과 촬영은 진행 중인 산책의 선택한 전봇대 카드에 있다.
- 영역표시는 거리와 GPS 오차를 더해 **20m 이내**, 사진 인증은 **10m 이내**다. 촬영 준비와
  셔터 양쪽에서 참여견·계정·산책 상태·최신 정상 위치를 확인하며, 일시정지 중에는 촬영하지 않는다.
- 사진 버튼을 누르면 먼저 서버 claim을 준비한다. 미점유뿐 아니라 PHOTO_REQUIRED나
  POLICY_UNDECIDED인 장소에서도 이 claim으로 사진 인증을 이어갈 수 있다.
- 서버가 claim 생성을 확인한 뒤 카메라를 연다. 따라서 이 단계 이후 카메라를 취소해도 이미
  만들어진 claim은 남는다. 미점유 장소에 만들어진 미인증 영역표시도 취소하지 않는다.
- 서버 카메라에는 실제 현장 촬영 안내만 보이며, 페이크 성공/실패 선택지는 없다.
- 사진 대기·재전송·재촬영·점유 변경 안내는 전봇대 카드 안에 표시한다. 일반 산책 카메라는
  기존 일기 사진/Pin 저장을 사용하며 게임 인증 대기열에 들어가지 않는다.

## 저장과 호출 순서

기존 게임 Room v1의 `territory_operation`에 `PHOTO` 작업을 저장한다. JSON 본문은
원본 mark의 identity, 서버 claim ID, 변경하지 않는 capture 요청을 담는다. 새 DB 스키마나
산책/일기 DB 마이그레이션은 없다.

1. 셔터 직전에 capture UUID, 촬영 시각, 위치·정확도·mock 여부, session/site ID를 고정해
   `CAPTURING` 작업을 저장한다. 같은 claim의 사진이 진행 중이면 새 촬영을 예약하지 않는다.
2. CameraX 파일을 앱 수명의 코루틴에서 `noBackupFilesDir/territory-photos/<capture UUID>.jpg`로
   복사한다. 최대 12 MiB를 검사하고 임시 파일 sync/rename 후 `PENDING`으로 바꾼다.
   화면 이탈보다 저장 작업이 오래 산다. 일반 산책 사진 폴더와 분리하고 백업/기기 이전에 포함하지 않는다.
3. `POST /attempts`에 원본 capture JSON을 보낸다. 티켓 응답의 capture/session/site/시각을
   대조하고 서버 photo ID를 저장한다. 응답 유실이나 티켓 만료에도 새 capture ID를 만들지 않는다.
4. `PUT /claims/{claim_id}/photos/{photo_id}`를 호출한다. 서버는 최초 연결 때 촬영 시각이
   claim 생성 이후이고 30초 이내인지, RECORDING인지 다시 검사한다. 같은 연결의 재전송은
   산책 종료 후에도 기존 결과를 돌려준다. 클라이언트 시계 오차는 서버 거절로 처리하며 시각을 고쳐 보내지 않는다.
5. 연결이 확인되면 PHOTO 작업을 `CONFIRMED/BOUND`로 바꾸고 뒤에 저장한 pause/end를 보낼 수 있게 한다.
   이미지 업로드와 판정 대기는 phase 전송을 막지 않는다. 파일 저장 중이거나 연결 응답이 불확실하면
   같은 세션의 후속 phase는 순서를 앞지르지 않는다.
6. 발급 URL과 헤더 그대로 사진을 PUT한다. 별도 업로더에는 앱 Bearer가 전달되지 않고 redirect도
   따라가지 않는다. create-only PUT의 409/412는 곧바로 성공 판정하지 않고 confirm의 객체 검사를 거친다.
7. `POST /attempts/{photo_id}/confirm`으로 판정을 요청한다. 큐 발행 실패/응답 유실은 같은 confirm으로
   복구한다. `GET /claims/{claim_id}`에서 현재 photo ID와 photo_status, resolution_code, site를 확인한다.
8. 완료 상태를 저장한 뒤 원본 파일을 정리한다. 재실행 때도 완료 파일 정리를 이어간다.

작업 순서는 Room 원본과 WorkManager가 소유한다. 저장 직후에는 앱 수명에서 빠르게 전달을 시도하고,
네트워크 오류는 WorkManager backoff로 복구한다. 지도 조회가 활성화된 동안에는 기존 15초 갱신에서
사진 판정도 조회한다. 프로세스 재시작은 산책 자체를 자동 재개하지 않으며 남은 연결 뒤 ENDED를 전달한다.
사진 저장 중 죽었으면 완성 파일이 있을 때만 전송하고, 없으면 촬영 실패로 남겨 종료를 막지 않는다.

## 재전송과 재촬영

| 결과 | 앱 동작 |
| --- | --- |
| 티켓/연결/PUT/confirm/조회 응답 유실 | 저장된 같은 capture·photo·파일로 이어가기 |
| 업로드 URL 만료 | 같은 capture POST로 티켓 재발급, 원본 증거와 사진 유지 |
| VISION_PENDING / claim PENDING | 현재 사진 판정 조회, 추가 촬영 잠금 |
| REJECTED | 같은 claim에서 현장 새 사진 촬영 |
| 최종 FAILED / claim RETRY_PENDING | 이전 사진 confirm을 반복하지 않고 같은 claim에서 새 사진 촬영 |
| VERIFIED + site_changed | 방문 인증은 완료지만 점유 승리로 표시하지 않음; 새 산책에서 다시 방문 |
| 최초 연결 거절 | 시각·위치를 고쳐 재전송하지 않고 현장 재촬영 안내 |
| 연결 후 원본 유실/복구 불가능한 업로드 거절 | 실패를 표시하고 새 산책 안내; 서버의 PENDING을 임의로 실패 판정하지 않음 |

최종 사진 판정은 서버가 수행한다. 앱은 기존 VLM의 강아지 존재 판정을 사용하며 대표견과 사진 속
강아지가 동일 개체라는 새로운 판정은 추가하지 않는다. 사진 인증 여부와 현재 점유는 별도 상태다.
현재 점유는 항상 서버 site의 소유자와 버전을 읽고, 지연된 이전 claim 결과로 덮어쓰지 않는다.

인증 성공 효과는 새 사진의 서버 결과가 내 강아지의 인증 점유를 확인한 경우에만 발생한다.
화면 재개방·프로세스 복구·숨김/일시정지 중 완료·다른 장소 결과는 뒤늦게 성공 효과로 재생하지 않는다.
계정 전환 후에는 이전 회원의 작업을 새 토큰으로 보내거나 새 회원 화면에 노출하지 않는다.

## 검증 범위

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.features.territory.*' --tests 'com.daengs.app.territory.*' --tests '*WalkViewModelTest' --tests '*WalkTerritoryUiTest' --tests '*TerritoryFeedbackUiTest' --tests '*WalkTrackingTest' :app:assembleDebug
```

가짜 서버로 티켓/연결/업로드/confirm 응답 유실, URL 만료, 동일 증거 재전송, 산책 종료·재시작,
계정 전환, 실패 후 새 사진, site_changed, 20m/10m와 셔터 재검증을 검사한다. 실제 Room 파일을 닫고
다시 열어 보존한 원본 사진과 요청으로 재개하는 테스트도 포함한다. localhost PUT에서 바이너리·
티켓 헤더·Bearer 부재·redirect 차단·create-only 충돌을 확인한다.

Compose 실제 카드/카메라 안내에서 서버 모드의 페이크 선택지 제거, 재촬영 버튼, 일반 일기 카메라
분리와 서버 안내 우선 표시를 확인한다. `app/build/reports/walk-territory/server-photo-recapture.png`는
`showMap=false`인 Robolectric 화면이다.

실제 배포 API·GCS·VLM에 사진을 전송하지 않았다. DB 적용과 서버 배포 후 실제 기기의 GPS,
CameraX, Naver SDK, Android 백그라운드 전달을 통합 검증하는 작업이 남아 있다.

## #205 후속: 인증 우선 정책 v2

DEV #335의 `certified-protection-v2` 시즌에서 미인증 영역은 즉시 사진 도전 가능하고
인증된 타견 영역은 `protected_until`까지 카메라 버튼을 차단한다. 표시할 카운트다운은
서버 시각과 단조 시간 경과로 계산하지만 종료 여부는 새 서버 응답으로 확인한다.
촬영 진입 전 photo-access 조회, 셔터 직전 challenge PUT을 거친다. claim은 유지하고
새 capture UUID마다 challenge를 만들어 같은 산책에서 재도전한다. 기존 사진 재전송은 별개다.
photo-access가 거절하면 reason을 고정된 정책 안내로 전달한다. 보호·시즌 종료를
위치/산책 상태 오류로 바꾸지 않으며 카메라를 열거나 사진 요청을 생성하지 않는다.
보호·경합·시즌 종료를 전송 복구 실패로 표시하지 않는다. 보호는 소유권 예약이 아니므로
촬영 중 바뀐 상태도 서버가 연결/판정 단계에서 재검증한다.
로컬 페이크 모드는 이 온라인 규칙의 시뮬레이션이 아니며 기존 debug/release 활성화 설정은 유지한다.
서버 SQL/웹/사진 worker/활동 집계기와 새 시즌을 먼저 준비해야 한다.
