# 점령 게임 1단계 — 모델·액션·페이크 공급자

[Geo 제작 계획](https://github.com/rkbuhtig/DAENGS_geo/pull/230)의 1단계다.
지도·카메라 연결 전, 화면 없이 점령과 인증 진행을 실행할 수 있는 기반을 추가한다.

## 구성과 기존 코드의 연결 지점

- `TerritorySite`/`TerritorySiteRepository`: 기존 중립 장소 조회·좌표 계약을 유지한다.
- `TerritoryClaimSite`: `siteId`로 위 장소와 결합하는 점유 스냅샷. `occupancy == null`이면
  미점유다. 점유에는 강아지, 인증 여부, 근거 세션·시도, 시각을 담는다.
- `SiteInteraction`/`evaluateClaimAccess`: 선택한 대상의 접근 가능 여부와 이유다.
  공유 점유와 독립이며 위치 공급자가 검증한 입력과 반경을 받는다. 테스트의 10m는 예시다.
- `ClaimSession`: `WalkSessionRow.id`로 이어지는 기존 로컬 산책 ID를 `clientSessionId`로
  사용한다. 산책 종료 뒤 생기는 서버 ID를 새로 만들어 점령 identity로 사용하지 않는다.
  `WalkSessionDogRow`의 참여견 중 선택한 대표견 ID와 행동 계정 ID를 입력받는다.
- `ClaimAttempt`: 세션·장소당 하나. 대표견을 고정하고 점유 처리 결과와 사진 상태를 분리한다.
- `InMemoryTerritoryClaimRepository`: 2단계 지도 작업과 프리뷰에서 사용할 페이크다.
  운영 DI에는 등록하지 않았으며 앱 재시작 시 사라진다. DB·인증·VLM 구현이 아니다.

`TerritoryBoardController.selectedSiteId`를 대상 선택에 사용하고 Pin의 영역표시 액션에서
접근 판정 후 `mark`를 호출하는 것이 다음 연결 지점이다. 기존 지도 화면·추적·Room 스키마는
이 PR에서 변경하지 않는다. 이 공급자의 동기 액션은 메모리 연산 전용이며, 온라인 단계에서는
코루틴 기반 I/O 어댑터로 감싸고 서버 결과로 상태를 갱신한다.

## 실행 흐름

```kotlin
val claims = InMemoryTerritoryClaimRepository(listOf(TerritoryClaimSite("site-a")))
val session = ClaimSession("walk-id", "user-id", "selected-pet-id")
// 실제 연결에서는 검증된 위치와 설정으로 evaluateClaimAccess를 호출한다.
val ready = SiteInteraction(session.clientSessionId, "site-a", ClaimAccess.READY)
val attempt = claims.mark(session, ready, "encounter-id", atMillis = 100)
claims.submitPhoto(attempt.attemptId, "same-session-camera-capture-id")
// 기다리는 동안 기존 점유는 그대로다. 페이크 결과는 별도 호출로 진행한다.
claims.resolvePhoto(attempt.attemptId, "same-session-camera-capture-id", ClaimPhotoOutcome.ACCEPTED, 200)
val occupied = claims.site("site-a")
```

`resolvePhoto`는 페이크 제어용이고 일반 액션 인터페이스에는 없다. 사진 결과를 서버가
확정하는 온라인 공급자가 UI로부터 임의 성공 결과를 받는 API로 구현해서는 안 된다.
촬영 참조의 세션·대상·접촉 근거 검증은 실제 카메라/서버 어댑터의 책임이다.

사진 결과는 성공·부적합·재시도 가능한 장애를 구분한다. 장애는 `resume`으로 같은 촬영을
재개하고, 부적합은 같은 시도에 새 촬영을 붙인다. 오래된 촬영의 콜백이나 다른 장소에
같은 촬영 ID를 쓰는 요청은 거부한다. 같은 세션·장소의 `mark`는 기존 시도를 반환한다.
시도의 `GRANTED`는 과거 점령 결과이며 현재 주인은 언제나 `site().occupancy`로 읽는다.

미인증 점유끼리의 무사진 경쟁은 `POLICY_UNDECIDED`로 드러내며 점유를 바꾸지 않는다.
이는 탈취 불가 정책의 확정이 아니다. 사진 인증을 붙이는 경로는 사용할 수 있다.
점유 버전이 바뀐 상태에서 늦은 인증을 확정하려 하면 `site_changed`로 멈춘다. 충돌을
최종 해결하는 정책은 온라인 계약 단계에 남겨두며, 자동 재탈취나 이탈·재진입 조건은 없다.

## 검증

`TerritoryClaimTest`가 APP/Dev 양쪽에 동일하게 둔 `territory-claim-scenarios.tsv`의 20단계
시나리오를 실행한다. 사진 없는 점유, 인증 강화, 재촬영·재시도, 여러 장소, 중복 요청,
새 세션의 인증 탈취, 미정 정책을 포함한다. 추가 테스트는 접근·대표견 고정·오래된 콜백·
다른 장소의 사진 재사용·점유 충돌·다음 산책의 내 영역 인증을 검증한다.

Android 환경에서는 `./gradlew :app:testDebugUnitTest --tests '*TerritoryClaimTest'`로 실행한다.
SDK가 없는 Windows 환경에서는 JDK 21과 Kotlin compiler 2.2.10, JUnit 4.13.2,
Hamcrest 1.3으로 아래 스크립트를 실행할 수 있다. Android 전체 빌드 검증을 대신하지는 않는다.

```powershell
./scripts/test-territory-claim.ps1 -JdkDirectory <jdk> -KotlinCompilerHome <kotlinc> -JunitJar <junit.jar> -HamcrestJar <hamcrest.jar>
```
