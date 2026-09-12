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

### 설정 제공 후 환경 확인

2026-09-12 사용자가 제공한 설정으로 개발 서버용 Debug APK를 다시 빌드했다.
APK 내부의 개발 API 주소·카카오 네이티브 키·네이버 키가 설정됐는지 확인했고,
네이버 키 ID는 연결된 폰의 설치 앱과 일치했다. 로컬 설정은 Git에서 제외되며 DB 암호와
서버용 비밀 키를 APK에 넣지 않았다.

- `https://daengapi.weareithero.cloud`는 설치된 출시 앱이 사용하는 서버다. health의 DB 상태는 정상이지만 미리보기 API 경로가 없다.
- `http://daengback.weareithero.cloud`는 health의 DB 상태가 정상이고 미리보기 경로 및 `diary-part-slots-v3` 계약을 제공한다. 이번 Debug APK는 이 개발 서버를 사용한다.
- 현재 작업 환경에서 서버 LAN 주소·DB 포트에 직접 연결되지 않아 제공된 DB 계정으로 로그인하거나 DB를 변경하지 않았다.
- 폰의 출시 앱을 열어 실제 산책 지도·장면 화면을 확인했다. 출시 APK와 Debug APK의 서명 인증서는 서로 다르다. 기존 앱 삭제가 필요한 교체 설치는 아직 수행하지 않았다.
- 제공된 공공데이터 키로 격자 (61, 125)의 초단기실황을 한 번 진단했고 HTTP 403 / `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`를 확인했다. 실제 기온 확보와 회원 미리보기 호출은 아직 미검증이다.

설정 포함 APK SHA-256: `d50f056e21c0392dc81ccca1c70e5d2f7c9189d4ef5b5073cdfae87597cd58a5`.
이 단계에서는 앱 코드 변경 없이 설정만 적용해 `:app:assembleDebug`를 실행했다.

### 기존 산책을 보존하는 별도 설치

출시 앱의 로컬 산책을 계속 검토할 수 있도록, `-PsideBySide=true`를 준 Debug는
`com.daengs.app.preview` / **댕스 미리보기**로 빌드한다. 저장 공간과 FileProvider도
패키지별로 분리된다. 옵션 없는 Debug와 Release는 기존 패키지·앱 이름을 유지한다.

```powershell
.\gradlew.bat :app:assembleDebug -PsideBySide=true --max-workers=2 --console=plain
apkanalyzer manifest application-id app/build/outputs/apk/debug/app-debug.apk
# 위 출력이 com.daengs.app.preview인지 확인한 뒤 새 앱으로 설치한다.
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.daengs.app.preview/com.daengs.app.MainActivity
```

같은 출력 경로를 사용하므로 설치할 APK의 패키지를 확인한다. 이미 설치한 미리보기 앱을
갱신할 때만 `adb install -r`을 사용한다. 원본 `com.daengs.app`의 삭제·데이터 초기화는 필요 없다.
서버에 동기화된 기록은 해당 서버의 동일 계정으로 로그인하면 기존 복원 흐름으로 내려받는다.
출시 서버와 개발 서버의 기록이 같다는 뜻은 아니며, 원본 앱의 로컬 전용 기록을 복사하지 않는다.

2026-09-12 연결된 Galaxy S25에서 다음을 확인했다.

- 별도 APK 빌드·서명 검증과 신규 설치 `Success`, `MainActivity` 실행 성공.
- 옵션을 켠 상태에서도 Release의 패키지 `com.daengs.app`와 앱 이름 리소스가 유지됨.
- 기존 Release 1.1.2의 설치/갱신 시각과 저장 경로가 유지됐고, 기존 산책의 지도·6개 장면을 다시 열어 확인함.
- 미리보기 앱에서 로그인 상태와 개발 서버 산책 3건 복원, 지도 표시와 6개 장면, 개발용 메뉴 진입을 확인함.
- 실제 회원 산책의 생성 버튼을 1회 눌렀으나 HTTP 404에 대응하는 “이 서버에서는 새 방식 미리보기를 아직 사용할 수 없어요.”가 표시됨. 생성 결과 검증은 완료되지 않음.

개발 서버 OpenAPI에는 경로가 있으며, 현재 DEV 라우터는 두 미리보기 플래그 중 하나라도 꺼져
있으면 404를 반환한다. 서버 설정을 직접 읽은 것은 아니므로 활성화 상태 확인이 남아 있다.
기존 일기 발행이나 원본 산책을 수정하지 않았으며, 실제 산책 화면 캡처는 로컬 검증 자료로만 보관한다.

별도 설치 APK SHA-256: `bfd883cc91b0840d865aa4b95023b38458557a389b3e43083e055bc5efbc424e`.
이번 변경은 빌드 패키지·매니페스트 이름 분리이며, 위 43개 테스트 뒤 앱 동작 코드를 추가로 바꾸지 않았다.

### 현재 장면의 설명 비교

`⋯ → 현재 장면 설명 비교`에서 준비한 장면을 PC에서 생성하고 `결과 확인`으로 읽는다.
같은 지도에서 **기본 설명 / 장면 설명**을 전환한다. `근거`는 실제 인용한 공간·환경·동선
자료와 각 파트의 선정 개수, 자료 부족을 표시한다. 시간은 한국시간으로 보여 준다.
기존 `com.daengs.app`을 보존하는 별도 `com.daengs.app.preview`에서 확인한다.

이전 장소 전용 도구는 원문·장면 중심을 모델 입력에서 빼고 DEV 슬롯 판정을 건너뛰었다.
현재 `tools/compare_diary_scenes.py`는 [DEV #474](https://github.com/SAJOYO/DAENGS_dev/pull/474)의
공통 수집·슬롯·writer를 DEV lock 환경에서 실행하는 얇은 진입점이다. 선정 로직이나
프롬프트를 앱 도구에 복제하지 않는다. 원래 records-first 장면 선정은 유지한다.

- v2 입력은 계정·산책·장면 ID·위치·시각·순서·제목·원문·사진/기록 ID에 더해
  `source_scene`의 원본 코어·관측을 해시로 묶는다. 중심만 바뀌어도 이전 결과를 거절한다.
- 편집·숨김·순서 변경 후에는 새 입력으로 비교한다. 배경은 화면에서 원문 앞에만 붙고,
  편집창에는 원문이 열린다. 비교를 기존 일기나 사용자 편집에 저장하는 경로는 없다.
- Release에는 메뉴와 파일 접근이 없다. Debug 교환 폴더는 기존
  `no_backup/diary-place-comparison/`를 유지한다. v1 결과는 새 입력에 적용되지 않는다.
- 앱 표시는 빈 원문도 그대로 유지한다. 다만 공통 DEV 작성 계약의 현재 PC 실행 범위는
  본문이 비어 있지 않은 2~12개 보드 장면이다. 다른 형식·빈 본문·범위 초과는 조회 전에
  중단하며, 원문을 채우거나 숨긴 장면을 되살리지 않는다.

앱에서 내보낸 bytes를 `adb exec-out run-as com.daengs.app.preview cat
no_backup/diary-place-comparison/request.json`으로 읽어 저장한다. PowerShell 문자열 변환
대신 Python `subprocess.check_output`의 bytes를 그대로 사용한다. 실제 `DiaryInput`과
그 분석의 canonical 업로드 좌표, 원래 `target_scene_count`도 필요하다. 실제 자료와
결과는 저장소 밖 개인 폴더에 둔다. DB를 접속하거나 가짜 산책을 만드는 도구가 아니다.

```powershell
uv run tools/compare_diary_scenes.py --dev-root <DEV폴더> --request <개인폴더>/request.json --source <개인폴더>/source.json --points <개인폴더>/points.json --target-scenes 5 --env-file <설정폴더>/.env --output <개인폴더>/새실행폴더
```

출력 폴더는 새로 만들어야 한다. 수집본·원응답, 보드, 전체 슬롯 판정, 모델 입력·스키마·응답,
프롬프트·정책·해시와 receipt를 남긴다. 재작성은 `--backgrounds <이전실행>/backgrounds.json`으로
동일 수집본을 사용한다. 키는 PC에서만 읽는다. 검토한 `result.json`만 앱 교환 폴더로 넣는다.
`accepted`는 구조·인용 검사 통과이고 의미 검토의 대체가 아니다.

2026-09-12 실제 산책 한 건으로 다음을 확인했다.

- 원래 6개 장면의 표시 필드가 전부 동일했다. canonical 입력 hash와 공개 장면의
  코어·좌표·시각·관측도 재현본과 일치했다. 발행본을 덮어쓰지 않는 별도 비교 revision이다.
- Place가 비어 실제 Kakao 공원/카페/음식점·주소를 조회했다. 위치 있는 4개 장면에는
  공간 3개씩, 관측 장면 1개에는 상대 저속 동선 1개가 선정됐다. 환경 자료는 0개였다.
- 같은 수집본으로 작성한 첫 2회에서 근거 없는 상점가·산책로 표현을 발견해 적용하지 않았다.
  현재 등록 정보의 의미를 명확히 한 세 번째 결과를 적용했다. 위치 없는 시작/종료는 원문 유지.
  동선 장면에는 실제 상대 속도가 추가됐고 시설 나열은 장면당 대표 1개로 줄었다.
- 문장은 아직 설명문에 가깝다. 지역 특성·날씨·감각이 풍부한 일기 품질까지 완료한 것은 아니다.
  현재 자료의 공백을 프롬프트로 꾸미지 않는다는 한계도 함께 기록했다.
- 실기기에서 설명 전환, 같은 선택 장면과 지도 유지, 근거, 원문만 열리는 편집창을 확인했다.
  기존 출시 앱을 삭제·교체하거나 산책·편집 원본을 수정하지 않았다.

이번 변경의 Android 모델/UI 6건, Debug 빌드, Release Kotlin 컴파일이 통과했다.
수집·입력 결합·부족 자료·인용의 회귀 검사는 공통 구현이 있는 DEV로 이동했다.
DEV의 검증 범위와 결과는 [파트 슬롯 문서](https://github.com/SAJOYO/DAENGS_dev/blob/feat/diary-scene-context-comparison/docs/walk/diary-part-slots.md)에 있다.
서버 슬롯 API의 활성화·배포는 아직이며, 이 결과는 PC에서 동일 DEV 코드와 실제 API를 실행한 비교다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -PsideBySide=true --tests '*DiaryPlaceComparisonTest' --tests '*DiaryPlaceComparisonUiTest' --max-workers=2 --console=plain
.\gradlew.bat :app:compileReleaseKotlin -PsideBySide=true --max-workers=2 --console=plain
uvx ruff check tools/compare_diary_scenes.py
```

실기기 APK SHA-256: `b30099cebc6704fcd25642c470839daafb4d7e495dea17e036e191a33c68f2e5`.
