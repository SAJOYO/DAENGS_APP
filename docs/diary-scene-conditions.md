# 장면 주소·기온 표시

장면 상세의 제목 → 시각 → 주소·기온 → 본문 순서를 유지한다.
주소는 SGIS 행정 읍면동 스냅샷이며 LLM 본문에서 추출하지 않는다.

## 작업 범위

- 기존 place_reference.facts의 sido/sigungu/dong/address_type을 앱에서 보존한다.
- 특별시·광역시는 짧은 도시명 + 시군구 + 읍면동, 나머지는 시군구 + 읍면동으로 표시한다.
- SGIS의 `수원시 영통구`를 분해하지 않는다. 행정동 숫자를 지우지 않는다.
- 과거 동만 있는 응답은 있는 값만 표시한다. 비어 있거나 충돌하는 주소를 추측하지 않는다.
- 이 APP 워크트리만 변경한다. DEV의 관계·서술 작업은 별도다.

## 기온 연결 경계

DEV의 별도 feat/diary-scene-conditions 브랜치에서 walk-diary-board-v1 장면의 선택 필드
`temperature`를 제공한다. 기존 서버 응답에는 필드가 없으며 배포 전에는 주소만 보인다.
APP은 temperature_c/unit/provider/observed_at/scene_at/grid/evidence_id를 읽고,
장면 시각 일치·기온 범위·실황 공급자·2시간 이내 과거 관측인지 확인한다.
산책 전체 기온이나 현재 날씨로 대체하지 않는다. 잘못된 기온은 숨기고 본문은 유지한다.
미제공 기온은 숨기고 0°C를 만들지 않는다. Preview 값은 예시다.
