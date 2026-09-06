# geo 장면 검토 실험

debug 앱의 **지난 산책 → geo 스토리보드 실험**에서 사용한다. 로그인이나 실제 산책이
없어도 예시를 선택할 수 있다. 별도 실행도 가능하다:

```sh
adb shell am start -n com.daengs.app/.ui.walk.GeoStoryboardLabActivity
```

핀 없음 / 핀 쏠림 / GPS 공백 / 속도 변화 / 정정·삭제 예시는 geo가 합성 GPS와 실제
공공데이터 캐시로 내보낸 파일이다. 환경 근거는 조회된 자료 범위에 한정되며 당시 사건의
원인을 설명하지 않는다. 자동 환경 지점은 행동 기록을 추가하지 않는다.

1. 핀 쏠림 예시에서 특별한 순간의 문구를 수정하고 킁킁 장면을 숨긴다.
2. 구성을 검토 완료한다.
3. 정정·삭제 예시를 선택한다. 같은 세션의 새 원본을 불러와 문구·숨김은 유지하고,
   정정된 장면에 재확인을 표시한다. 삭제된 원본의 편집 문구는 현재 검토본에서 제외한다.
4. 화면을 닫았다 열어 저장 여부를 확인한다. 다른 예시의 편집은 세션별로 분리된다.

JSON 불러오기는 geo lab의 **app 장면 JSON 저장** 파일을 받는다. 형식 오류, 중복 ID,
시간 역전, 없는 출처 참조, 1MB 초과 파일은 거절하며 기존 검토본은 유지한다.
현재 개발 화면은 synthetic=true만 허용한다. 로컬 파일을 원자적으로 교체해 저장하고 실제 산책
Room DB, 서버 동기화, AI 생성은 연결하지 않는다. activity와 예시 자산은 debug 전용이다.

계약 원본: DAENGS_geo의 `docs/contracts/walk-storyboard-candidates-v1.md` 및 JSON Schema.
app의 `GeoStoryboardBundle`이 검증하고 기존 `StoryboardContent`와 편집 결합 함수를
재사용한다. 원본 장면 payload는 검토 스냅샷에 포함하지만 사용자 문구로 덮어쓰지 않는다.
장면 ID가 바뀐 재분석 결과는 다른 장면이며 자동 유사도 매칭은 하지 않는다.

고정 파일은 `src/debug/assets/storyboard`, 동일한 계약 회귀 테스트 입력은
`src/test/resources/storyboard`다. 갱신할 때 두 폴더와 geo의 fixtures를 함께 맞춘다.
