# 서버 게임 세션·영역표시 — APP 연결 2단계

후속 [사진 인증 연결 #165](territory-server-photos.md)에서 같은 서버 액션 설정에 인증 촬영을 추가했다.
아래는 #164 당시의 구현 범위와 복구 계약이다.

[APP #164](https://github.com/SAJOYO/DAENGS_APP/pull/164)는 [점유 조회 #161](territory-server-browsing.md)에
게임 세션과 미인증 영역표시를 연결한다. 서버 계약은 [Dev #260](https://github.com/SAJOYO/DAENGS_dev/pull/260)이다.
운영 DB 마이그레이션·서버 배포는 실행하지 않는다.

## 실행 범위

| 빌드 설정 | 게임 동작 |
| --- | --- |
| 기본 debug | 서버 점유 둘러보기만 (#221에서 기본값 변경) |
| `-PterritoryServerRead=false` | 로컬 점유·페이크 사진 연습 |
| `-PterritoryServerRead=true` | 서버 점유 둘러보기만 |
| `-PterritoryServerActions=true` | 서버 점유 조회 + 게임 세션 + 영역표시 |
| release | 서버 점유 조회·액션 기본 활성화 (#402) |

Actions가 켜지면 Read를 따로 지정하지 않아도 조회 공급자를 사용한다. 같은 `API_BASE_URL`과
공용 `SessionProvider`를 사용한다. Release에서도 같은 액션 경로를 사용하며 로컬 연습으로
폴백하지 않는다. Debug 온라인 테스트는 아래 옵션으로 빌드한다.

```powershell
./gradlew.bat :app:assembleDebug -PterritoryServerActions=true
```

시작 전에는 점유만 둘러본다. 산책 중에는 세션 등록/재개가 서버에서 확인되고, 참여견이 있으며,
최신의 정상 GPS와 **거리 + 오차 ≤ 20m**, 알려진 미점유 장소일 때 영역표시 버튼을 연다.
관측 시각·좌표·정확도·mock 여부를 서버에 보내고 서버가 접촉을 다시 판정한다.
사진 인증 우선권은 서버의 기존 정책을 유지하며 미인증끼리의 탈취 규칙을 새로 정하지 않는다.

대표견은 장소별로 선택한다. 요청을 저장한 뒤에는 결과가 불확실한 동안에도 선택을 고정한다.
대기 상태를 표시하며 서버 응답 전에는 점유를 바꾸지 않는다. 서버 모드의 **인증 촬영은 아직 닫혀 있다**.
일반 산책 사진 카메라·일기 Pin은 계속 기존 저장 흐름을 사용한다.

## 저장·전송·복구

- `WalkTrackingService`가 원래 산책 UUID와 함께 실제 시작 시각을 공개한다. 등록 본문의
  `started_at`과 정렬한 참여견 UUID 목록을 최초 한 번 저장하고 재전송 때 그대로 사용한다.
- Application의 `TerritoryActionSync`가 산책 상태를 관찰한다. 화면에서 레이어를 숨기거나
  나가도 일시정지·재개·종료를 저장한다. 영역표시 시점에도 현재 산책과 계정을 다시 확인한다.
- 전용 Room `daengs_territory.db` v1에 등록/phase/영역표시 작업을 순서대로 남긴다.
  산책 기록 DB v11과 연결되는 외래키가 없어서 짧은 산책 정리에도 미확인 요청이 사라지지 않는다.
  새 DB와 journal/WAL은 클라우드 백업·기기 이전에서 제외한다. 기존 산책 DB 마이그레이션은 없다.
- **회원 + 산책 + 장소 UNIQUE**로 버튼 연타와 중복 저장을 막는다. 영역표시의 원본 JSON을
  HTTP 전에 저장하고, 전송 사실도 응답 전에 기록한다. 취소/timeout/응답 파싱 실패는 성공이 아니다.
- 영역표시를 저장하면 앱 수명 범위의 코루틴에서 즉시 전송을 시도한다. 화면 호출자가 취소되거나
  WorkManager가 backoff 중이어도 이 경로는 독립적으로 시작한다. 실패·프로세스 종료는
  WorkManager의 네트워크 조건과 지수 backoff로 복구한다. 같은 세션의 작업은 저장 순서를
  지키고 일시 오류 뒤 후속 작업을 앞질러 보내지 않는다. 다른 세션은 따로 진행할 수 있다.
  앱 시작과 서버 지도 복귀에서도 저장된 작업을 다시 찾는다.
- `territory-actions`는 등록/phase/영역표시/사진 연결까지만 처리한다. 연결된 사진의 업로드와
  판정 조회는 별도 `territory-photos` Worker가 맡아 사진 판정 대기가 새 요청을 막지 않는다.
  즉시 전송도 기존 전송 mutex를 사용하며 원본 관측 시각을 갱신하지 않는다.
- phase는 GET으로 현재 버전을 읽고 PATCH의 `expected_version`까지 저장한다. 응답 유실은
  같은 PATCH를 재전송한다. `session_changed`로 명확히 거절되면 GET으로 버전을 복구한다.
  서버가 이미 ENDED인 세션을 RECORDING으로 되돌리지 않는다.
- 영역표시 응답 유실은 **같은 대표견·관측 시각·좌표를 담은 같은 POST**로 복구한다.
  서버는 이미 저장한 원본이면 산책 종료/위치 만료 뒤에도 기존 시도를 반환한다.
  프로세스가 재시작돼도 산책 자체를 자동 재개하지 않으며, 남은 요청 뒤에 ENDED를 전달한다.
- 서버가 `stale_location`, `OUT_OF_RANGE`, `UNTRUSTED_LOCATION`, `site_not_nearby`,
  `NOT_RECORDING`으로 접촉을 명확히 거절했을 때만 새 위치로 다시 눌러 보낼 수 있다.
  이 경우는 서버 시도를 소비하지 않은 요청이다. 새 요청은 현재 phase 뒤에 저장한다.
  통신 오류와 `attempt_identity_conflict`에는 원본을 바꾸지 않는다.
- 각 전송 전에 토큰의 회원과 작업 소유자를 대조한다. 계정 전환 중 돌아온 결과는 원래
  회원의 저장 행에만 남기며 새 회원의 지도/성공 효과에 노출하지 않는다. 타인의 대기열은 전송하지 않는다.

## 점유와 성공 효과

공유 조회의 `site.occupancy`와 버전이 현재 점유의 근거다. 현재 계정의 저장된 확정 MARK 응답
(사진 판정으로 갱신한 응답 포함)을 장소별 최대 버전으로 합친다. 조회 캐시보다 높은 버전만
반영하므로 B 결과가 도착해도 A의 확정 표시가 사라지지 않고, 재시작 후에도 복원된다.
더 최신인 조회 결과가 우선하며, 조회 실패·계정 전환으로 점유를 모르는 상태에서는 과거 응답만으로
점유를 확정하지 않는다. 성공 효과용 receipt는 별도 일회성 신호이며 상태 복원이 이를 재생하지 않는다.

처음 보낸 요청의 정상 응답이 실제 내 강아지의 점유를 확인했을 때만 일회성 성공 신호를 낸다.
응답 유실 복구·새 화면의 과거 결과·숨김/일시정지 중 결과·다른 장소 결과는 성공 애니메이션으로
뒤늦게 재생하지 않는다. 서버 응답 대기로 사용자가 닫은 장소 카드를 다시 열지 않는다.

## 검증과 남은 연결

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.map.features.territory.*' --tests 'com.daengs.app.territory.*' --tests '*WalkViewModelTest' --tests '*WalkTerritoryUiTest' --tests '*TerritoryFeedbackUiTest' --tests '*WalkTrackingTest' :app:assembleDebug
```

가짜 서버로 요청 연타·다견·여러 장소·응답 유실·phase 순서/CAS 충돌·계정 전환·불완전 응답·
종료 후 복구를 검사한다. 실제 Room 파일을 닫고 다시 열어 원본 POST와 작업 순서 보존도 확인한다.
localhost HTTP는 PUT/POST 본문·Bearer·오류 코드·좌표 7자리 계약을 확인하고,
PATCH 버전/재전송은 주입한 서버 계약 테스트로 검사한다.

Robolectric의 실제 Compose 카드에서 대기 중 대표견 잠금, 일반 카메라 분리, 서버 결과 표시를
확인한다. 화면 캡처는 `app/build/reports/walk-territory/server-mark-pending.png`와
`server-mark-confirmed.png`다. `showMap=false`이며 실기기 GPS/Naver SDK/백그라운드 스케줄링이나
실제 배포 API와의 통합을 검증한 것은 아니다.

다음 3단계는 사진 증거·티켓 발급·시도 연결·업로드·판정 조회와 복구다. 서버의 영역표시 20m와
인증 촬영 10m를 구분하고, 최종 사진 실패는 같은 claim에서 새 사진을 찍도록 연결한다.
