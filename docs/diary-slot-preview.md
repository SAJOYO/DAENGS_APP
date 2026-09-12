# 산책 일기 개발용 미리보기

**Debug 빌드에서만** 산책 기록을 열고 **일기 메뉴(⋯) → 개발용 일기 미리보기 → 미리보기 만들기**를 누른다.
#311의 공통 지도 상세 메뉴에 연결된다. 장면 준비가 끝나면 메뉴에서 실행할 수 있다.
Release에서는 메뉴 진입점과 화면 호출을 차단한다.

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
- 서버가 반환한 슬롯 정책 버전을 표시한다. 현재 정책은 `diary-part-slots-v3`이고 API 응답 형식은 계속 `walk-diary-slots-preview-v1`이다.
- 격자 기온은 관측값·관측 시각·기록보다 앞선 시간(초)·격자(nx, ny)·공급자·공급자 기준 시각·자료 조회 시각·관측 의미를 표시한다.
  시각은 서버가 보낸 시간대 표기를 유지한다. 관측값이 없거나 null이면 0도나 현재 날씨를 만들지 않는다.
- 각 자료의 ‘근거 원문 보기’에서 출처 ID·버전·병합한 출처 목록·판정 수치와 알려지지 않은 공급자 필드까지 확인한다.
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
격자 기온은 [#462](https://github.com/SAJOYO/DAENGS_dev/pull/462)의 계약을 소비한다.
서버의 기록 맥락 수집 worker/Beat와 `DAENGS_WALK_ENTRY_CONTEXT_ENABLED`, Life의
`DATA_GO_KR_KEY` 및 기상청 단기예보 조회서비스 활용 권한이 필요하다. 앱이 기온을 직접 수집하지 않는다.
자료가 없거나 너무 오래된 관측은 환경 슬롯이 빈다. 현재 자동 수집 대상은 행동·글 기록이며,
사진 전용·경로 보충·시작/종료 장면까지 기온 자료가 항상 제공되는 것은 아니다.
2026-09-11 DEV 실호출 기록의 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`는 아직 성공으로 재검증하지 않았다.

## 검증 범위

`DiarySlotPreviewTest`는 요청 경로·인증·JSON·소유권·동기화 순서·잘못된 산책 응답·취소를 확인한다.
`DiarySlotPreviewScreenTest`는 명시적 생성·중복 탭·재시도·희소 자료·세 파트 표시·메뉴 진입을 확인한다.
v3 기온의 시각·격자·인용·출처 원문 펼치기와 360dp/글꼴 1.3배 화면도 확인한다.
`DiarySlotEvidenceTextTest`는 누락/null과 유효한 0도·0초를 구분하고 공급자 시간대를 보존하는지 확인한다.
변경한 기존 상세 메뉴의 소비자는 `WalkDiaryMapScreenTest`, `WalkSessionDetailUiTest`다.
`WalkDiaryPublicationLifecycleTest`로 기존 20초 마감·오프라인 기본 결과·재진입·늦은 응답 보존을 함께 확인한다.

테스트 fixture `diary-slots-preview-v1.json`은 DEV v2 CLI 합성 산책의 실제 Gemini 출력을
API 응답 모양으로 감싼 것이다. `context_pending: false`, 빈 `excluded_backgrounds`는 테스트에서 추가했다.
실제 회원 산책·운영 DB를 호출한 결과가 아니다. `DiarySlotResources.kt`로 API와 화면 테스트에서 함께 읽는다.

`diary-slots-preview-v3.json`은 DEV `694029482ce5566748df258bd596544e554a2fc0`의 실제
선정·기온 판정·서술 조립·응답 스키마를 실행해 내보냈다. 입력은 합성 산책과 합성 기온이며,
writer 응답은 고정 대역이다. 기상청·Gemini 실호출 결과가 아니다. 유효한 관측 1개,
허용 나이를 넘긴 관측 1개, 환경 자료가 없는 기록 1개를 포함한다.
v2 실제 Gemini fixture를 유지해 이전 응답과의 호환성도 확인한다.

재생성에는 DEV의 위 커밋을 checkout한 소스 경로가 필요하다. DB·서버 설정·외부 API 키는 필요 없다.

```powershell
uv run tools/build_diary_slot_fixture.py --dev-src ../DAENGS_dev/backend/src
```

생성기 의존성은 DEV lock과 같은 pydantic 2.13.4, tzdata 2026.3이다.
생성 파일 SHA-256: `8df734acdf3d7cca6ed14589f6ef2f76d0d5fffa1f168337b82f976dc7bd24ad`.

```powershell
.\gradlew.bat :app:assembleDebug :app:generateReleaseBuildConfig :app:testDebugUnitTest --tests 'com.daengs.app.walk.diary.DiarySlotPreviewTest' --tests 'com.daengs.app.ui.walk.DiarySlotPreviewScreenTest' --tests 'com.daengs.app.ui.walk.DiarySlotEvidenceTextTest' --tests 'com.daengs.app.ui.walk.WalkDiaryMapScreenTest' --tests 'com.daengs.app.ui.walk.WalkSessionDetailUiTest' --tests 'com.daengs.app.walk.diary.WalkDiaryPublicationLifecycleTest' --max-workers=2 --console=plain
```

2026-09-11 초기 구현에서는 `dev`의 `4dfb0044` 위에서 API/미리보기 화면/지도 상세/공통 상세 네 클래스의 31개를 확인했다.
API/로더 10개·기존 지도 상세 13개·공통 상세 3개가 첫 실행에서 통과했다.
새 화면 5개 중 숫자 표기 기대값(`23.0` 대신 JSON 왕복 뒤 `23`) 한 건을 수정하고,
주소·상권 표시를 보완한 뒤 새 화면 5개를 다시 실행해 모두 통과했다. 최종 실패·skip은 0이다.
Debug APK 빌드도 성공했다. 전체 테스트, 실기기 설치, 실제 회원 산책·운영 DB 호출은 수행하지 않았다.

개발용 화면을 Debug로 제한한 후 `:app:assembleDebug :app:generateReleaseBuildConfig
:app:testDebugUnitTest --tests 'com.daengs.app.ui.walk.DiarySlotPreviewScreenTest'`를 실행했다.
화면 5개와 Debug 빌드가 통과했고 생성된 Release `BuildConfig.DEBUG=false`를 확인했다.
Release APK 실행을 확인한 것은 아니다.

### 2026-09-12 최신 계약 검증

APP `05fa5ea`(#311 포함)를 합친 PR head `7d986fb` 위에서 v3 표시를 반영했다.
선택한 6개 클래스의 최종 결과는 **43 passed / 0 failed / 0 skipped**다.

| 클래스 | 최종 통과 |
| --- | ---: |
| DiarySlotPreviewTest | 11 |
| DiarySlotPreviewScreenTest | 6 |
| DiarySlotEvidenceTextTest | 2 |
| WalkDiaryMapScreenTest | 16 |
| WalkSessionDetailUiTest | 3 |
| WalkDiaryPublicationLifecycleTest | 5 |

첫 43개 실행에서 기존 화면 기대값 1개가 새 조회 시각 표시 때문에 실패했다.
기대값과 여러 자료에 공통인 조회 시각의 선택자를 고친 뒤 화면 클래스 6개만 재실행해 통과했다.
앱 코드는 최초 실행 뒤 바꾸지 않았다. 전체 테스트를 실행한 결과는 아니다.

Debug APK 생성, APK v2 서명 검증, Release `BuildConfig.DEBUG=false`, `git diff --check`를 확인했다.
Robolectric의 360dp·글꼴 1.3배 캡처에서 생성 결과·근거·기온 정보를 확인했다.
캡처는 `app/build/outputs/diary-slot-preview-{result,evidence,temperature}.png`이며 실제 폰 캡처가 아니다.
최초 XML과 재실행 기록은 `app/build/reports/diary-slot-verification/`에 보관했다.

이 세션의 APK에는 서버 주소·카카오·네이버 설정을 주입하지 않았다. 실제 회원 산책 검증에는
기존 `local.properties` 설정으로 다시 빌드하고, 서버의 두 미리보기 플래그와 회원 로그인을 준비해야 한다.
연결된 기기의 기존 앱은 Release 1.1.2로 확인했으며 설치·삭제·실제 회원 산책 API 호출은 수행하지 않았다.
기상청 인증 및 실제 기온→문장 연결도 성공으로 보고하지 않는다.
