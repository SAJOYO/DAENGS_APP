# 서버 산책 일기를 앱에서 읽기

서버 [DAENGS_dev #378](https://github.com/SAJOYO/DAENGS_dev/pull/378)의
`walk-diary-response-v1` / `walk-diary-bundle-v1`을 기존 산책 목록·지도·검토 화면에 연결한다.
새 경로는 `WalkDiarySync`, 이전 후보 계약은 `WalkStoryboardSync`가 맡는다.

## 요청과 복구

1. 기존 순서대로 GPS finalize → 행동·메모/위치 확정 → 사진 메타데이터 동기화를 마친다.
2. `GET /app/walks/storyboard/capabilities`에서 `diary_formats`를 확인한다.
   404 또는 빈 목록이면 기존 v2~v5 분석을 사용한다. 통신 오류나 새 형식 오류에서는
   구형 생성으로 바꾸지 않는다.
3. 새 형식을 지원하면 `GET /app/walks/{id}/storyboard?bundle_format=walk-diary-bundle-v1&target_scene_count=5`로 조회한다.
4. 결과가 없거나 stale/failed이면 같은 경로에 POST한다. `expected_entries`와
   GET에서 확인한 `expected_photo_manifest`를 함께 보낸다. 로컬 사진이 있으면 먼저
   ACK된 publisher/revision과 서버 manifest가 같은지도 대조한다.
5. 정상 캐시에는 POST하지 않는다. 명시적인 갱신은 `refresh=true`로 한 번 요청한다.
   `running`은 `refresh=false` POST 한 번으로 서버의 lease를 확인한다. 살아 있으면 재사용하고
   프로세스 종료 등으로 60초 lease가 만료됐으면 복구한다. 이후 최대 8번, 2초 간격으로
   조회하고 완료되지 않으면 WorkManager 재시도 흐름에 넘긴다.

목표 5장은 이번 연결의 기본 보충 목표다. 사용자 기록이 5개를 넘으면 모두 남고,
근거가 부족하면 5개 미만으로 남는다. 목록 페이지 크기나 LLM 호출 수 제한과 다르다.

## 저장과 최신성

Room 테이블이나 버전은 바꾸지 않는다. 기존 `walk_scene_analysis.bundle`에 새 응답
전체를 저장해 모델 상태·사진 버전·준비 진단을 보존한다. parser가 형식별로 분기한다.
`GeoStoryboardBundle`은 기존 화면 연결용 표현 모델이며 새 응답을 구형 wire JSON으로
변환하는 것은 아니다.

새 로컬 stamp는 `diary:` 접두사와 기록 stamp·사진 목록·사진 ACK 상태의 hash를 가진다.
저장 직전 DAO 트랜잭션에서 현재 계정·원본 stamp·generation을 다시 비교한다.
목록 제목과 지도도 사진 변경을 관찰하므로 삭제된 사진을 이전 일기로 되살리지 않는다.
옛 stamp는 이전 알고리즘 그대로 읽는다. 사용자 편집과 검토 사본은 별도 테이블에 유지한다.

생성 실패와 모델 문장 실패는 다르다. 서버가 `ready`와 `model_status=unavailable`을
반환하면 원본 장면을 보여주고 배경 문장 실패를 안내한다. 강제 갱신 전까지 자동으로
LLM을 반복 호출하지 않는다. 새로운 입력에 대한 결과가 stale/failed/running이면
현재 입력으로 검토 완료할 수 없다.

## 화면과 원본

- 산책 목록의 제목은 서버 일기 제목이며 개별 장면 제목은 기록 종류에 따른 앱 표제다.
- 장면 배경은 기울임 서술, 직접 남긴 기록은 별도 영역에 표시한다. 배경 문구를 편집해도
  메모 원문·행동 코드·사진·지도 위치는 바꾸지 않는다.
- 주소는 명시적 동 정보가 있을 때 별도 표시한다. 현재 서버의 주변 시설 공급자는
  아직 동/공원/하천의 상세 관계를 공급하지 않는다.
- 행동·사진은 서버가 반환한 위치 방법을 유지한다. 추정/마지막 위치를 관측 GPS처럼
  표시하지 않는다. 체류·속도 장면은 기존 원본 GPS 순번·시각·chain 대조를 통과해야 지도에 찍는다.
- 사진 ID는 기기 파일과 연결한다. 이미 서버 장면에 포함된 사진을 별도 카드로 다시 추가하지 않는다.
  숨김도 유지한다. 다른 기기에는 사진 파일이 없을 수 있어 촬영 기기에서 볼 수 있다고 안내한다.
- 지도 핀의 숫자는 장면 순서다. 같은 위치의 여러 장면은 각 순번을 함께 표시한다.

## 검증 자료와 범위

`app/src/test/resources/storyboard/diary-v1.json`은 Dev 커밋
`f3b18bf3d2f60ac4ec63a26cc45238817a64b6bf`의 합성 record/observation 재료를
`prepare_stamps` → `assemble_diary` → `DiaryStoryboardResponse`로 검증·직렬화한 응답이다.
문장은 고정된 가짜 writer 응답이다. 실제 Gemini 호출이나 실제 사용자 산책 데이터가 아니다.
메모·행동·체류·사진을 포함하며 목표 5개 중 4개만 준비된 경우다.

타겟 검증은 새 parser/Room 동기화·기존 후보 호환·일기 투영·목록 reader·지도/검토
Composable을 다룬다. 명령과 실행 결과는 이 작업 PR의 `확인한 것`에 기록한다.
새 Composable에는 Preview를 제공한다.

서버 기능 `DAENGS_WALK_DIARY_ENABLED`는 기본 false다. 이 앱 작업은 서버 설정을
활성화하거나 실제 Gemini를 호출하지 않는다. 개발 서버 활성화 후 실제 산책 한 건으로
생성 시간·비용·문체와 지도까지 확인하는 작업은 별도로 남아 있다.
