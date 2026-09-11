# 산책 일기 개발용 미리보기

**Debug 빌드에서만** 산책 기록을 열고 **일기 메뉴(⋯) → 개발용 일기 미리보기 → 미리보기 만들기**를 누른다.
기존 일기를 준비하는 화면의 진입 버튼도 Debug에서만 표시한다. Release에서는 두 진입점과 화면 호출을 차단한다.

이 화면은 개발자가 생성 결과와 선정 자료를 비교하는 실험 도구다. 슬롯 개수·선정 근거·인용 정보는
서비스용 일기 화면에 노출하지 않는다. 서비스 연결 시 사용자는 완성된 일기를 읽으며,
이번 변경은 그 서비스 화면이나 발행 경로를 구현한 것이 아니다.

현재 계정의 종료된 산책 한 건을 기존 동기화기로 맞춘 뒤 서버 산책 ID로
`POST /app/walks/{walk_id}/diary-slots/preview`를 호출한다.
요청은 `target_scene_count: 3`, `generate: true`이며 슬롯 정책은 서버 기본값을 따른다.
장면 수 3은 기존 선정기의 목표값이다. 사용자 기록 보존과 시작·종료 장면 때문에
응답 전체가 정확히 3개로 제한되는 것은 아니다.

화면 진입·재구성은 모델을 호출하지 않는다. 생성 버튼을 누를 때만 실행하고 진행 중 중복 탭을 막는다.
‘다시 만들기’는 그 시점의 저장 자료로 새 요청을 보낸다. 자료 수집을 기다리는 자동 반복은 없다.
서버의 일반 일기 확정·발행 시간 제한과 별개인 미리보기 API이며 클라이언트 읽기 제한은 60초다.
화면을 닫으면 결과 전달을 취소한다. 이미 시작한 서버 처리가 취소된다고 보장하지 않는다.

## 화면에서 보는 것

- 생성된 장면의 제목·본문과 공간·환경·동선별 선택 자료 수.
- ‘장면 자료 보기’를 펼치면 배경을 더하기 전 기본 장면과 선택된 자료를 표시한다.
- 모델이 인용한 자료를 구분한다. 인용 ID가 유효하다고 문장 의미까지 검증된 것은 아니다.
- 주소는 위치 설명으로 표시하며 공간 슬롯 수에 더하지 않는다.
- 자료 준비 중, 서버 비활성/미배포, 동기화 미완료, 로그인 만료, 모델 실패를 구분한다.
- 모델이 실패해 기본 장면을 돌려주면 실패 안내와 함께 그 장면을 보여준다. 자료가 없는 파트는 0으로 표시한다.

미리보기는 메모리에만 남고 닫으면 사라진다. 기존 발행 보드·장면 편집·Room 일기 캐시에 저장하지 않는다.
생성 전 동기화는 기존 기록·핀·사진 전송을 수행하지만 `includeStoryboard = false`로 일기 생성을 요청하지 않는다.
원래 상세 화면의 기존 발행 수명주기는 그대로 동작한다.
요청 전후 회원·로컬 산책 소유권·서버 ID와 응답의 `client_session_id`를 확인한다.
계정 변경·삭제·취소 이후 도착한 결과는 표시하지 않는다.

## 서버 실행 조건

DEV [#454](https://github.com/SAJOYO/DAENGS_dev/pull/454)의 API가 있는 서버에서
`DAENGS_WALK_DIARY_ENABLED=true`와 `DAENGS_WALK_DIARY_SLOTS_PREVIEW_ENABLED=true`가 모두 필요하다.
두 번째 플래그는 기본 false다. 앱에서 서버 설정을 변경하지 않는다.
저장된 공간 자료를 쓰려면 해당 서버의 맥락 수집도 동작해야 한다.
실제 환경 자료 수집기는 이 연결에 포함되지 않아 환경 슬롯이 비어 있을 수 있다.

## 검증 범위

`DiarySlotPreviewTest`는 요청 경로·인증·JSON·소유권·동기화 순서·잘못된 산책 응답·취소를 확인한다.
`DiarySlotPreviewScreenTest`는 명시적 생성·중복 탭·재시도·희소 자료·세 파트 표시·메뉴 진입을 확인한다.
변경한 기존 상세 메뉴의 소비자는 `WalkDiaryMapScreenTest`, `WalkSessionDetailUiTest`다.

테스트 fixture `diary-slots-preview-v1.json`은 DEV v2 CLI 합성 산책의 실제 Gemini 출력을
API 응답 모양으로 감싼 것이다. `context_pending: false`, 빈 `excluded_backgrounds`는 테스트에서 추가했다.
실제 회원 산책·운영 DB를 호출한 결과가 아니다. `DiarySlotResources.kt`로 API와 화면 테스트에서 함께 읽는다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.DiarySlotPreviewTest' --tests 'com.daengs.app.ui.walk.DiarySlotPreviewScreenTest' --tests 'com.daengs.app.ui.walk.WalkDiaryMapScreenTest' --tests 'com.daengs.app.ui.walk.WalkSessionDetailUiTest' --max-workers=2 --console=plain
```

2026-09-11, `dev`의 `4dfb0044` 위에서 위 네 클래스의 31개를 확인했다.
API/로더 10개·기존 지도 상세 13개·공통 상세 3개가 첫 실행에서 통과했다.
새 화면 5개 중 숫자 표기 기대값(`23.0` 대신 JSON 왕복 뒤 `23`) 한 건을 수정하고,
주소·상권 표시를 보완한 뒤 새 화면 5개를 다시 실행해 모두 통과했다. 최종 실패·skip은 0이다.
Debug APK 빌드도 성공했다. 전체 테스트, 실기기 설치, 실제 회원 산책·운영 DB 호출은 수행하지 않았다.

개발용 화면을 Debug로 제한한 후 `:app:assembleDebug :app:generateReleaseBuildConfig
:app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.DiarySlotPreviewScreenTest'`를 실행했다.
화면 5개와 Debug 빌드가 통과했고 생성된 Release `BuildConfig.DEBUG=false`를 확인했다.
Release APK 실행을 확인한 것은 아니다.
