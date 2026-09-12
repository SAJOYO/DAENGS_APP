# 실제 산책 원본 대조

2026-09-12, APP #335. 기존 UI의 합성 기록 검증 다음에 실제 불일치 기록과 비교 기록을 넣었다.
**509m/951m 차이를 동일 서버 원본으로 재현했으며, 정상 경로의 장면 강조 누락도 발견했다.**
이 PR은 진단 도구와 재현 결과다. 측정 정책이나 제품의 장면 연결 규칙을 변경하지 않는다.

## 입력 출처와 비교 범위

- 원래 설치 앱 `com.daengs.app`: 1.1.2 / code 7, 마지막 업데이트 2026-09-11 20:15:43.
  Play 서명, non-debuggable이며 이번 작업에서 교체하거나 데이터를 지우지 않았다.
- 사용자 확인으로 `.preview`와 카카오 계정은 같다. 설치 APK의 설정을 대조하니 원래 앱은
  출시 HTTPS API(`daengapi.weareithero.cloud`), `.preview`는 개발 HTTP API(`daengback.weareithero.cloud`)다.
  회원이 같다는 사실만으로 두 서버의 산책 데이터가 같다고 볼 수 없다.
- 별도 `.walkreview` 앱으로 출시 API에 로그인하여 서버 기록 7건을 조회했다. 원래 앱의
  로컬 목록은 8건이었다. 두 목표 기록은 서버에 있었고, 나머지 1건의 업로드 여부는 조사 범위 밖이다.
- 두 기록의 detail과 **저장된 storyboard v4**를 GET으로 받았다. 이 서버는 diary_formats가
  비어 있고 v5 조회는 409였다. 재생성 요청 없이 읽을 수 있는 이전 형식을 선택했다.
- APP 계산은 `summarizeLegacy(..., Int.MAX_VALUE)`를 **명시적 비교 기준**으로 실행한다.
  과거 원본에 motion 정책·단조 시간·기기 속도가 없으므로 신규 정책을 복원했다고 표시하지 않는다.
- 서버 비교는 DEV `11e4dad4cbb6a2eb73341e709365dacdc291a0d9`의 순수 `compute_walk_facts`,
  calculation_version 4를 로컬 실행했다. 출시 서버의 실행 바이너리/커밋을 확인한 것은 아니다.

이 입력은 **서버에 보존된 원본 좌표**다. 원래 앱의 비공개 Room DB 원본과 바이트 단위로
동일한지 확인하지 못했다. 서버 좌표의 소수점 정밀도, 원래 APK의 정확한 소스 버전,
로컬 사용자 편집·사진은 이 비교에서 복원하지 않았다. 장면은 저장된 서버 사본이다.

## 같은 입력으로 나온 값

| 비교 항목 | 불일치 사례 A (원래 509m) | 비교 사례 B (원래 270m) |
|---|---:|---:|
| 서버 원본 관측 수 | 363 | 174 |
| Kotlin 기존 정책 거리 | 509.498396m → 표시 509m | 271.531737m → 표시 272m |
| Kotlin 기존 활동 시간 | 390.998초 → 06:30 | 164.139초 → 02:44 |
| 같은 Kotlin 계산에서 속도 상한만 해제 | 951.100505m | 271.531737m |
| 속도 상한·3m 최소 거리 모두 해제 | 974.683519m | 279.845698m |
| 서버 연결 거리 합계 | 959.792954m → 960m | 279.845698m → 280m |
| 서버 moving_distance_m | **951m** | 278m |
| 서버 duration_s / moving_s | 388 / 318 | 164 / 161 |
| Kotlin 경로 유지 / 최소 거리 생략 / 고속 제외 | 128 / 209 / 26 | 72 / 102 / 0 |
| 현재 장면 연결 결과 | CONNECTED 0 / NO_ROUTE 6 | CONNECTED 2 / NO_ROUTE 3 |

사례 A에서 속도 상한만 바꾸면 약 **441.60m**가 더해진다. 기존 앱의 고속 판정은 마지막
수용점과 다음 점을 비교한다. 서버 facts는 7m/s 상한 없이 연결 거리와 이동 중 거리를
계산한다. 서버의 951m는 `moving_distance_m`와 맞으며, 전체 연결 거리 960m와는 다르다.
상한을 없앤 Kotlin 값도 반올림하면 951m지만 **같은 의미의 계산이 된 것은 아니다.**

서버에서 인접 연결 속도 >7m/s인 26개 선분은 464.26m / 40.82초다. 이것을 앱의 제외
거리와 그대로 빼면 안 된다. 최소 거리 생략과 마지막 수용점 비교가 연결의 양 끝을 바꾼다.
또 A의 시작 전 첫 관측 1개는 서버가 제외하지만 기존 앱은 유지한다. 정확도·점프·공백·
mock에 의한 서버 제외는 두 사례 모두 없었다.

사례 B는 속도 상한을 없애도 값이 같다. **270m와 복원 결과 272m의 차이는 미해결**이다.
단순 반올림 차이나 좌표 정밀도 탓으로 확정하지 않는다. 원래 앱의 DB와 정확한 reader를
확보한 뒤 입력 순번·좌표·시간·정책을 대조해야 한다.

## 장면과 시작·끝에서 발견한 것

`legacyReviewTrace`는 별도 GPS 판정기를 만들지 않고 실제 `TrailRecorder.add`의 결과를
추적한다. 묶음 `summarizeLegacy` 거리와 일치하는지도 검사한다.

| 사례 / 장면 | 원본 관측의 실제 처리 | 현재 장면 연결 | 해석 |
|---|---|---|---|
| A / 2·3·4 | seq 110·193·294, 3m 미만 생략 | NO_ROUTE | 고속 제외가 아닌데 경로에 없는 꼭짓점이라는 이유로 강조가 빠짐 |
| A / 5 | seq 350, 고속 제외 | NO_ROUTE | 경로 밖 관측 핀은 보존되고 선·방향 강조는 없음 |
| A / 6 | seq 362, 고속 제외 + 종료 사건과 관측 시각 차이 | NO_ROUTE | 끝난 뒤 장면을 마지막 보행점으로 옮기지 않음 |
| B / 2·4 | seq 33·146, 경로에 유지 | CONNECTED | 실제 기기에서 장면 주변 선·방향 강조 확인 |
| B / 3 | seq 85, 3m 미만 생략 | NO_ROUTE | 정상적인 연속 경로 위에서도 같은 강조 누락 재현 |
| A·B / 시작, B / 마무리 | 관측은 유지되지만 제어 사건 시각과 다름 | NO_ROUTE | 사건 시점과 첫/마지막 위치의 시점을 따로 나타낼 필요 |

A의 마지막 경로점은 seq 339, 11:01:28.816이고 기록 종료는 11:02:08.279다.
그 사이 **39.463초**를 실제 도착 위치로 주장하지 않는다. 이후 seq 340~362는 기존
속도 판정에서 제외된다. 앞부분의 seq 6·7·11도 고속 제외되어 총 26개다.
탐색 패널은 기록 시간과 동선 시간을 구별하고, 지도 표시는 `동선 끝`을 사용한다.
시작 쪽에는 기존 reader가 기록 시작보다 397ms 이른 관측을 포함하는 문제도 남아 있다.

현재 모델은 실제 고속 제외와 최소 거리 생략을 구별할 근거를 장면 대응에 전달하지 않는다.
이 확인으로 #333의 합성 테스트 통과를 **실제 기록 전체의 대응 성공으로 해석할 수 없음**이
드러났다. `NO_ROUTE` 한 값만 보고 “보행에서 제외됨” 또는 “위치를 모름”을 추론하면 안 된다.

## 후속 수정 계약

1. 원본 관측의 판정과 지도 꼭짓점 생략을 구별한다. 생략한 관측은 자신이 속한 확정 연결
   구간을 참조할 수 있어야 한다. 단순 최근접선 fallback은 고속 제외점도 다시 붙이므로 금지한다.
2. 시작·종료 **사건**과 첫·마지막 확인 **위치**의 시간 주소를 분리한다. 단순 시각 불일치
   조건을 없애는 것으로 GPS 전후에 이동 경로를 만들어서는 안 된다.
3. 서버의 옛 951m 문구를 현재 보행거리처럼 보이지 않게 측정 버전과 거리 정의를 연결한다.
   사진·메모·사용자 편집 본문을 자동으로 고쳐서 숫자를 맞추지 않는다.
4. 비교 사례 B의 2m 차이는 별도 입력 동등성 문제로 남긴다. 원본 확보 전 270m를 정답으로
   정하거나 필터 상수를 조정하지 않는다.

먼 재개·왕복·chunk 경계 등 이전 합성 회귀 조건은 그대로 유지한다. 이번 두 실제 사례에는
여러 보행 구간으로 나뉜 기록이 없으므로, 실제 다중 구간 검증을 끝냈다고 할 수 없다.

## 검토 앱 실행과 원본 재현

`tools/walk-record-review.init.gradle`을 명시한 debug 빌드만 `.walkreview`를 사용한다.
전용 Application은 일반 `DaengsApp`, 산책 Room/WalkRuntime/GPS/업로드/장면 생성을 시작하지 않는다.
회원 로그인·token refresh 외 산책 통신은 허용 목록의 GET만 가능하며 HTTP redirect를 따르지 않는다.
서버 origin은 설치 시 고정하여 다른 origin으로 인증을 재사용하지 않는다.

환경 변수에 검토할 서버와 SDK 설정을 넣는다. 값은 저장소 밖에서 관리한다.

```powershell
# DAENGS_REVIEW_API_URL = 원래 기록을 보유한 HTTPS origin (마지막 / 없음)
# DAENGS_REVIEW_KAKAO_KEY = 해당 앱의 Kakao native key
# DAENGS_NAVER_NCP_KEY_ID = 지도 SDK 설정
.\gradlew.bat -I tools/walk-record-review.init.gradle :app:assembleDebug `
  -PslimAbi=arm64-v8a -PversionName=walk-review-335 --max-workers=2
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.daengs.app.walkreview/com.daengs.app.ui.walk.review.WalkRecordReviewActivity
```

설치 출력 `Success`를 확인한다. 로그인 후 목록에서 기록을 고르면 저장된 좌표와 장면을
읽어 기존 지도·구간·장면 UI에 넣는다. 검토 화면의 수정/추가 버튼은 동작하지 않는다.
재분석 실패/미지원 장면은 원본을 먼저 보관하고 오류로 알리며 새 장면을 생성하지 않는다.

각 조회는 앱의 `no_backup/real-record-review/<owner>/<session>/<capture>/` 아래에
`detail.json`, `capabilities.json`, `storyboard.json`, `report.json`을 저장한다.
report에는 원본 SHA-256, origin, 버전, 시간·거리·장면 연결, 순번별 처리 내역과 상수 하나씩
바꾼 비교 결과가 있다. 로그인 자격증명은 이 디렉터리에 내보내지 않는다.

```powershell
uv run --no-project tools/pull_walk_record_review.py `
  --adb <adb.exe> --serial <device-serial> --output <private-capture-root>
uv run --no-project tools/replay_walk_review.py `
  --case <capture-folder-containing-report.json> --backend <DAENGS_dev/backend>
```

pull 도구는 JSON 경로·원본 해시를 검증하고 변경된 기존 캡처를 덮어쓰지 않는다.
서버 재생 도구는 명시한 DEV checkout의 함수만 로컬 실행하고 소스 커밋·dirty 여부·함수 파일
해시를 결과에 기록한다. API/DB 호출은 없다. 원본 좌표, 계정 ID, 장면 본문, 화면 캡처는
개인 진단 폴더에 보관하고 Git/PR에 포함하지 않는다.

## 검증 결과

- 별도 debug `assembleDebug` 및 관련 7개 클래스 **67개 테스트 통과**, 실패·오류·skip 0.
  `LegacyReviewTraceTest`, `CompletedRouteReviewTest`, `WalkRouteExplorerStateTest`,
  `RouteExplorerIndexTest`, `TrailRecorderTest`, `WalkSummaryTest`, `WalkPaceTest`를 선택했다.
- SM-S931N 실기기: 실제 두 기록 조회, 전체 지도, 장면 선택·방향 강조/무강조,
  기록/동선 시간 패널, 목록으로 돌아가기, 원본 추출과 서버 재생 확인.
- 원래 앱 1.1.2의 버전·업데이트 시각은 유지됐다. 다른 `.preview` 앱도 교체하지 않았다.
- 이 결과는 진단 작업의 완료다. 위 후속 수정 계약 및 실제 다중 구간 검증이 남아 있다.
