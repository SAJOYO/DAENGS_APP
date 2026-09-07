# 점유 상태별 전봇대 그림

사용자가 고른 매끈한 만화 전봇대를 기본으로, 낡은 질감과 짙은 밑동이 있는 그림을 점유 상태로 표시한다. 살짝 비스듬한 시점, 짧고 얇은 금속 가로대, 왼쪽에 감긴 해진 흰 포스터를 유지한다.

| 상태 | 에셋 | 지도 문구 |
| --- | --- | --- |
| NEUTRAL | map_territory_pole_neutral.webp | 선택하면 기존 장소/점유 설명 |
| UNVERIFIED | map_territory_pole_occupied.webp | 미인증 |
| VERIFIED | map_territory_pole_occupied.webp | 인증 |

`WalkMapPresentation`의 기존 점유 상태를 사용한다. 더러움은 점유 유무를 뜻하며 시간이나 방문 횟수에 따라 누적하지 않는다. 미인증과 인증은 같은 에셋을 공유하고 기존 caption과 카드로 구분한다. 기존 원형 배경/점유색 테두리는 제거했다. 접근 링과 잠깐 나타나는 성공 발자국은 기존 반경·시간·색상을 유지한다.

## 에셋과 배치

- 내장 image_gen 도구로 이 작업의 승인된 시안을 편집했다. 외부 에셋 팩이나 외부 저장소 그림이 아니다.
- 기본 원본: `exec-8438d7b8-437b-4a22-a530-d101d32a0953.png` (매끈한 시안에서 전봇대만 분리).
- 점유 원본: `exec-f63a653c-b5e9-4298-8ff2-c195b84a207b.png` (기본 실루엣을 참고해 점유 시안의 질감/밑동 적용).
- 생성기가 RGB 매트를 반환했으므로 반입 과정에서 밝은 외부 배경을 제거했다. 포스터·애자의 흰 내부는 어두운 외곽선으로 보호한다. 완전한 alpha 입력이면 이 과정을 건너뛴다.
- 원본은 작업 드롭 폴더의 `map/territory_pole_{neutral,occupied}.png`에 보관했다. 앱은 저장소의 WebP만 사용하므로 생성 도구의 로컬 경로에 의존하지 않는다.
- 반입: `uv run tools/import_room_assets.py <drop>` → `map_sprite.py` → 기존 WebP 품질 92. 기존 방·견종 반입 동작은 유지한다.
- 두 파일 모두 **256×640**, 밑동 접점 **(128,624)**. 바깥 배경을 자른 뒤 밑면 중심을 맞추므로 가로대가 좌우 중심을 바꾸어도 밑동은 움직이지 않는다.
- 네이버 마커는 **48×120px**, 선택 시 **60×150px**. 성공 확대도 가로·세로를 함께 키운다. anchor는 **(0.5,0.975)**라 그림을 확대해도 밑동의 지리 좌표가 유지된다. 원형 아이콘처럼 가운데를 좌표에 놓지 않는다.
- 정적인 에셋 두 개를 캐시하며 애니메이션 프레임마다 비트맵을 만들지 않는다.

## 검토 방법

`TerritoryFeedbackPreview`는 지도와 같은 픽셀 크기/접점을 사용한다. `TerritoryPoleArtTest`는 실제 WebP를 읽어 배경 alpha, 흰 포스터 보존, 밑동 명도 차이, 비율을 검증하고 `app/build/reports/territory-pole/native-sizes.png`를 출력한다. 이 그림은 실제 크기 래스터 비교이며 지도 스크린샷은 아니다.

Debug 빌드의 `TerritoryPoleLabActivity`는 실제 `MapHost`와 전봇대 레이어에 가상 장소 세 개를 전달한다. 기본·미인증·인증, 선택 확대, 상태 전환, 링/발자국을 서버 점령이나 GPS 기록 없이 검토한다. 이 진입점은 release에 포함되지 않는다.

```powershell
adb shell am start -n com.daengs.app/.ui.walk.TerritoryPoleLabActivity
```

대상 검증: `test_map_sprite.py`, `TerritoryPoleArtTest`, `TerritoryFeedbackUiTest`, `WalkViewModelTest`, `:app:assembleDebug`. 설치·실화면 확인 결과는 PR에 기록한다.

검증 결과: 에셋 반입 테스트 3개, 앱 대상 테스트 23개 통과. 디버그 빌드 성공. 에뮬레이터 설치 `Success` 확인 후 APK를 회수해 두 WebP의 SHA-256이 저장소와 일치함을 확인했다. 물리 기기는 확인하지 않았다. 로컬 지도 클라이언트 키 미설정(`Authorization failed [800] Client is unspecified`)으로 지도 타일을 불러오지 못하므로 거리 배경과 겹침은 키가 있는 환경에서 확인해야 한다.

검토 중 에뮬레이터의 설치 APK가 외부 작업으로 교체되어 마지막에는 전용 Activity가 존재하지 않았다. 이전 빌드의 native overlay 표시 확인까지 유효하며, 최종 세 상태 동시 표시·터치·가로/세로 검토는 같은 빌드가 유지되는 환경에서 진행해야 한다. 현재 에뮬레이터에 이 PR 빌드가 남아 있다고 보장하지 않는다.

아래는 실제 표시 픽셀 크기다. 왼쪽부터 기본·미인증·인증이고 각 쌍은 기본 크기·선택 크기다. 위는 밝은 배경, 아래는 어두운 배경이다.

![전봇대 실제 픽셀 크기 비교](images/territory-pole-native-sizes.png)
