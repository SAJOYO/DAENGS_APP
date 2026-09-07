# 활동 통계 API 연결

서버 기준: [DEV #281](https://github.com/SAJOYO/DAENGS_dev/pull/281),
`aa4d086a7f9206f6df51ba866ad23799aa36ad70`의 `schemas/activity.py`와 `routers/activity.py`.

`DaengsApp.activityRepository`가 공용 `SessionProvider`와 `TokenStore`를 사용한다.
화면·Worker에서 필요한 시점에 아래 suspend 함수를 호출할 수 있다.

```kotlin
val activity = (context.applicationContext as DaengsApp).activityRepository
val link = activity.sessionLink(clientSessionId).getOrThrow()
val walks = activity.walkSummary(ActivityWalkWindow(fromMs, toMs, petId)).getOrThrow()
val territory = activity.territorySummary(seasonId, petId).getOrThrow()
```

| 함수 | HTTP |
|---|---|
| sessionLink | GET /app/activity/sessions/{client_session_id} |
| walkSummary | GET /app/activity/walks/summary?from_ms=…&to_ms=…[&pet_id=…] |
| territorySummary | GET /app/activity/territory/{season_id}/pets/{pet_id} |

기간은 산책 **종료 시각** 기준 `[fromMs, toMs)`이며 epoch milliseconds, 최대 366일이다.
petId 생략은 계정 전체 조회다. 시즌 ID는 호출자가 전달하며 앱이 현재 시즌을 만들거나 추정하지 않는다.
서버에는 현재 시즌 목록 조회 API가 이 세 경로에 포함되어 있지 않다.

측정값 null은 0으로 바꾸지 않는다. 산책 PENDING, 점령 PENDING/READY/STALE, 제외 이유,
generation·version·source revision을 응답 그대로 보존한다. 점수의 holdingUnits는 BigInteger이며
Double로 변환하지 않는다. 사실 통계와 점수, 통계 확정 시각과 점수 기준 시각은 별개다.

401/404/422/503는 `ActivityHttpException.statusCode`와 선택적인 `code`로 받는다.
`503 activity_disabled`는 빈 성공 결과가 아니다. 네트워크·파싱 오류도 Result.failure로 전달하고,
코루틴 취소는 다시 던진다. 로그인 정보가 없으면 HTTP를 보내지 않는다. 조회 중 계정·로그인 세션이
바뀌면 늦은 결과를 폐기한다. 다른 요청에서 refresh token이 회전한 경우도 보수적으로 폐기한다.

이 연결은 호출 가능한 조회 계층만 등록한다. 시작 시 자동 요청, 폴링, 결과 디스크 캐시, 화면,
게임 규칙/안내 변경, 서버 flag 전환, 시즌 생성, 시설 AI 변경은 포함하지 않는다.
실제 서버 조회에는 #281 배포·DB 준비 및 서버 `activity_game_enabled` 활성화가 필요하다.

검증 범위: 로컬 HTTP 서버로 세 경로·Bearer 인증·기간/대상 에코·오류 상태·nullable 값·정수 정밀도를
검증하고, 저장소 경계에서 세션 변경과 취소 전파를 검증한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daengs.app.activity.*" --tests "com.daengs.app.auth.SessionProviderTest"
```

운영 계정으로 실제 서버를 호출하거나 실기기에 설치한 검증을 뜻하지 않는다.
