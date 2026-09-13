# 신규 motion 기록 대조 — APP #366

2026-09-13. [열람 기준 #365](walk-reading-baseline.md)을 유지하며, 신규 기록을 화면에 연결하기
전에 입력과 계산 근거를 대조하는 단계다. 이번 변경은 **검토 도구와 서버 지원 진단**이다.
신규 실기록 한 건의 종단 검증이나 제품 화면의 측정 전환을 완료한 것은 아니다.

## 실기기에서 확인한 선행 문제

동일 카카오 계정 여부는 사용자가 이미 확인했다. 실제 차이는 **앱이 접속하는 서버의 배포 상태**다.

| 확인 대상 | 2026-09-13 03:19 KST 결과 |
| --- | --- |
| 출시 앱·검토 앱의 `https://daengapi.weareithero.cloud` | motion 백업/측정·trajectory 후보 API가 공개 OpenAPI에 없음 |
| 개발 서버 `http://daengback.weareithero.cloud` | motion 백업/정밀 백업/계산 및 trajectory 후보 API 등록됨 |
| 출시 서버의 로그인된 검토 앱 요청 | `motion-capabilities`, `trajectory-capabilities` 둘 다 HTTP 422. 이름을 `walk_id` UUID로 파싱하다 거절 |
| 기존 확보 preview 로컬 사본 | `walk_recording_epoch`, `walk_motion_backup`, `walk_motion_precision` 모두 0행 |
| 원래 출시 앱의 로컬 DB | 배포 빌드라 `run-as` 접근 불가. 원본 관측·정책·완료 영수증을 직접 확인하지 못함 |

**서버에 신규 기록이 0건이라는 결론은 내릴 수 없다.** 출시 서버에서 지원 API에 닿지 못해
완료 백업 목록 조사 자체가 진행되지 않았다. 개발 서버도 공개 경로 등록만 확인했으므로
DB 백업 테이블·해당 계정의 완료 기록 존재 여부는 아직 미확인이다.

DEV #475는 머지돼 있다. 이후 개발 서버 배포 작업
[#34710567172](https://github.com/SAJOYO/DAENGS_dev/actions/runs/34710567172)도 성공했지만,
그 작업의 Walk 확인 주소는 서버 PC의 `http://127.0.0.1:8000/openapi.json`이다.
그 성공이 출시 HTTPS 서버의 반영을 뜻하지 않는다. 기존
[실기록 대조 문서](walk-real-record-review.md)에도 두 서버의 구분이 있다.

출시 origin/인증을 개발 서버로 바꿔 쓰지 않았다. 출시 배포·DB 변경도 수행하지 않았다.

## 이번에 구현한 것

- `MotionRecordReviewActivity`: 기존 `.walkreview` 검토 APK에서 ADB로만 여는 진단 진입점.
  manifest의 `android.permission.DUMP`로 일반 앱의 호출을 제한한다. 제품 manifest에는 없다.
- `MotionRecordReviewCapture`: 지원 여부와 목록 페이지를 읽고, 각 기록의 백업 상태를
  `complete / collecting / not_found / conflict / unavailable`로 구별한다. API 미지원은
  빈 완료 목록으로 바꾸지 않는다. UUID를 지정하면 그 기록의 입력과 후보 응답을 내보낸다.
- `MotionReviewHttp`: 기존 `SessionProvider` 안에서 인증하며, 전체 실행 동안 같은 계정
  generation을 확인한다. 고정 HTTPS origin·허용한 GET 경로·리다이렉트 거부·응답당 32MiB
  상한을 둔다. 토큰과 요청 헤더는 내보내지 않는다.
- `MotionReviewReplay`: 백업 계약을 검증하고 **실제 Kotlin motion 엔진**으로 순번별
  위치 판정·수용/제외 사유·보행 연결·거리 기여·누적거리를 산출한다. legacy 판정기를 쓰지 않는다.
- `compare_motion_record_review.py`: 지정한 DEV 체크아웃에서 동일 백업을 재생한다. Kotlin과
  Python의 최초 판정/누적거리 차이, HTTP motion 결과, trajectory 원장·구간·경계·위치·사건
  시각을 대조한다. 입력/정책 차이는 `incomparable`, 같은 근거의 결과 차이는 `mismatch`,
  API 실패는 `unavailable`로 구별한다.

`WalkMotionContract`와 `WalkPrecisionContract`의 기존 manifest/chunk/완료 영수증 검증을
재사용한다. 선택한 기록은 진단 API와 같은 10,000점 상한을 지키며 자르지 않는다.
정밀 백업이 완료돼 있으면 원래 좌표 비트를 사용하고, 404면 6자리 서버 좌표라는 별도
근거를 붙인다. 정밀 백업이 진행 중이거나 손상됐으면 완료 입력으로 인정하지 않는다.

기존 지도·장면·화살표·재생·서랍 코드와 거리 정책에는 변경이 없다. Room 복원, 백업 업로드,
finalize, 장면 생성, 활성 측정 채택은 호출하지 않는다. 인증 만료 때 기존 provider의 토큰
갱신만 발생할 수 있다.

## 결과의 의미

`status.state=complete`는 **파일 수집이 끝났다**는 뜻이다. API 실패 응답도 진단 근거로
남으므로 계산 대조 통과와 다르다. 비교 도구의 `same_backup_comparison=equivalent`는
같은 서버 백업을 사용한 Kotlin 재생·지정 DEV 소스·받아온 후보가 대응했다는 뜻이다.

모든 결과의 `independent_device_input_verified`는 현재 **false**다. 정밀 좌표 비트가
백업돼 있다는 사실과, 기록 당시 기기 DB를 독립적으로 확보해 대조했다는 사실은 다르다.
이번 도구에는 기기 원본의 독립 수집/대조 기능이 없다.

단조 시간·원본 순번은 정수로 비교하고 반올림하지 않는다. 거리의 허용오차는 기존 계약의
절대 `1e-7 m`, 상대 `1e-10`이다. 수치 동등성과 원장 digest/measurement ID의 동일성을
구별한다. 후보의 ETag·결과 digest·ID·계정/산책 scope도 확인한다. 뒤늦게 도착한 정밀
백업 때문에 계산 기준이 바뀌면 두 결과를 같은 입력으로 통과시키지 않는다.

관측 연결 정책은 여전히 `motion-shadow-observed-v1` 실험값이다. 이 대조가 기존
motion-v1의 재현을 확인해도 새 연결 정책의 실세계 정확도나 장면 화면 통합을 입증하지 않는다.

## 실행

공개 API 등록 여부만 확인할 때는 인증이 필요 없다.

```powershell
uv run tools/probe_motion_review_api.py --origin https://daengapi.weareithero.cloud --origin http://daengback.weareithero.cloud --output ../private-motion-review/api-readiness.json
```

검토 APK 빌드 방식은 [기존 대조 도구](walk-real-record-review.md)를 따른다.
`DAENGS_REVIEW_API_URL`·`DAENGS_REVIEW_KAKAO_KEY`를 설정한 뒤 기존 init script로 빌드한다.
같은 origin·서명·package로 `adb install -r`하고 **Success**를 확인한다.

```powershell
# 지원 여부와 서버 기록의 백업 상태 조사
adb -s SERIAL shell am start -n com.daengs.app.walkreview/com.daengs.app.ui.walk.review.MotionRecordReviewActivity
# 지원이 확인된 뒤 완료 기록 한 건 지정
adb -s SERIAL shell am start -n com.daengs.app.walkreview/com.daengs.app.ui.walk.review.MotionRecordReviewActivity --es walk_id SERVER_WALK_UUID
adb -s SERIAL shell run-as com.daengs.app.walkreview ls no_backup/motion-record-review
uv run tools/pull_motion_record_review.py --adb PATH_TO_ADB --serial SERIAL --capture CAPTURE_UUID --output ../private-motion-review/captured
uv run tools/compare_motion_record_review.py --case ../private-motion-review/captured/motion-record-review/CAPTURE_UUID --backend ../DAENGS_dev/backend --output ../private-motion-review/comparison.json
```

수집은 UUID 디렉터리를 새로 만들며 실행 중/실패한 자료는 완료 대조에 쓰지 않는다.
응답은 정확한 UTF-8 문자열을 JSON envelope의 `body`에 보존하고 `sources.json`의 SHA-256과
대조한다. 내보내기는 이 진단 디렉터리만 읽으며 경로 탈출·중복·기존 자료 변경을 거부한다.
개인 좌표·시각·계정 식별자가 포함되므로 산출물은 저장소 밖에 둔다.

## 검증 기록

2026-09-13, `walk-review-366`, arm64-v8a:

- `assembleDebug` 성공, **4개 클래스 40개 Kotlin 테스트 통과**(실패/오류/건너뜀 0).
  새 수집 테스트 8개와 기존 `WalkMotionSyncTest`, `WalkPrecisionSyncTest`, `DesignLockTest`.
- 실제 Kotlin 엔진이 기존 공통 **32개 합성 fixture**를 수집/재생했다. 그 출력으로 Python
  비교 검증 **13개 테스트 통과**. 32건의 순번별 판정·거리·경계·공백·제외·정밀 근거를 확인했다.
  HTTP 후보 응답은 이 검증에서 합성했으며 **배포 API 실기록 성공으로 세지 않는다**.
- 변조된 소스/해시, 진행 중 백업, 순번/정책 오류, 계정 교체, 페이지 순환, 늦은 정밀 결과,
  틀린 표시 경계, 재사용된 측정 ID, API 미지원, 나노초 정밀도를 검증했다.
- SM-S931N에 `adb install -r` **Success**. 새 진단 Activity를 실제로 실행해 출시 서버의
  422 두 건을 수집했다. 이후 기존 장면 검토 Activity로 돌아갔다.
- 설치에 사용한 APK SHA-256:
  `74f7f8e15e2d8e81e5b2d3e4ee0bca13959f1dabefb9e489081357d023183eb5`.
  이번에 서랍/지도 UX를 새로 실기기 검증했다고 주장하지 않는다.
- Python 대조에 사용한 DEV 체크아웃: `11e4dad4cbb6a2eb73341e709365dacdc291a0d9`, 수정 없음.
  비교 산출물은 사용한 체크아웃 commit·수정 여부·읽은 계산 소스 해시도 남긴다.

합성 Kotlin 출력을 다시 만들 때는 저장소 밖 **새 디렉터리**를 지정한다. 기존 출력 재사용은
수집기에서 거절한다. Gradle 테스트가 up-to-date면 해당 테스트 작업을 재실행한다.

```powershell
$env:DAENGS_MOTION_REVIEW_FIXTURE_OUTPUT = 'C:\private-motion-review\fresh-goldens'
.\gradlew.bat :app:testDebugUnitTest --tests '*MotionRecordReviewCaptureTest' --rerun
uv run tools/tests/test_motion_record_review.py --backend ../DAENGS_dev/backend --kotlin-suite $env:DAENGS_MOTION_REVIEW_FIXTURE_OUTPUT -v
```

## 다음 완료 조건

1. **출시 서버의 반영 범위를 먼저 확인한다.** GCP의 실행 revision·백업/정밀 백업 테이블과
   지원 API를 확인하고 필요한 변경을 별도로 검토한다. 개발 서버 배포 성공만으로 대체하지 않는다.
2. 같은 출시 origin에서 완료 motion 기록을 확보한다. 기기 원본의 관측 순서·정책·epoch·
   완료 영수증과 서버 백업을 독립 대조할 수 있는 수집 경로도 필요하다. 현재 자료로는 미완료다.
3. 검증된 기록의 관측 구간·보행 구간·공백·기록 사건을 현재 열람 화면에 공급한다.
   이때 신규 API를 legacy 판정기로 다시 해석하지 않고, #365의 선택/카메라/서랍 계약을 유지한다.

출시 API 지원이 확인되기 전 실제 기록 대조나 다음 화면 연결을 완료 처리하지 않는다.
