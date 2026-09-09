# 산책 사진 메타데이터 동기화

[App #235](https://github.com/SAJOYO/DAENGS_APP/pull/235) /
[Dev #372](https://github.com/SAJOYO/DAENGS_dev/pull/372).
일기 이관의 입력 연결 단계이며 새 화면이나 이미지 업로드를 추가하지 않는다.

사진 파일은 기존 앱 전용 저장소에 남는다. 서버에는 사진 ID, 촬영 시각, 위치 샘플 시각,
위치와 정확도만 전송한다. 기존 행동/메모와 점령 인증 큐에 섞지 않는다.
서버 계약의 정본은 Dev의 `docs/walk/photo-metadata.md`와 해당 PR이다.

Room 12→13은 `walk_photo_sync`와 스키마 13을 추가한다. 신규 로컬 산책은 사진이 0개라도
게시자를 가진다. 기존 사진이 있는 세션은 migration에서 등록하고, 서버 복원의 빈 세션은
등록하지 않는다. 출처를 모르는 옛 빈 목록은 미수집으로 남긴다.
기존 사진 파일·동선·행동·메모·미전송 핀 상태는 보존한다.

사진 저장/삭제와 revision 증가는 같은 Room 트랜잭션이다.
네트워크 요청은 `pendingPayload`에 먼저 고정하고 응답을 잃으면 그대로 재전송한다.
요청 중 추가/삭제가 생기면 그 편집은 더 높은 revision으로 남는다.
옛 ACK가 새 편집을 완료 처리하지 못하며, 계정이 바뀌면 요청과 ACK 반영을 멈춘다.
목록 삭제는 서버 tombstone으로 이어지고 삭제 ID를 다시 살리지 않는다.

기존 WalkSync에서 GPS 확정·기록 동기화 뒤, storyboard 동기화 전에 사진을 맞춘다.
사진 저장/삭제가 기존 WorkManager를 깨우고 앱 시작의 pending 복구도 이 대기열을 포함한다.
서버 capability가 없거나 비활성이면 전송만 건너뛰며 원본과 대기열은 유지한다.
409는 기기/버전 충돌이므로 자동 덮어쓰기하지 않는다. 현재 Worker의 영구 실패 처리로 남으며
충돌 해소 UI는 별도다.

서버는 DB migration과 `DAENGS_WALK_PHOTO_METADATA_ENABLED` 활성화가 필요하다.
이번에는 운영 설정이나 실기기 설치를 하지 않았다. 실제 일기 생성 format 연결·LLM 호출,
이미지 업로드/다른 기기에서의 사진 파일 복원은 후속 작업이다.

검사는 다음 다섯 클래스에 한정했다. Kotlin 컴파일과 Room 스키마 생성도 함께 수행한다.

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.daengs.app.walk.sync.WalkPhotoSyncTest' --tests 'com.daengs.app.walk.store.WalkPhotoStoreTest' --tests 'com.daengs.app.walk.store.WalkMigrationTest' --tests 'com.daengs.app.walk.sync.WalkSyncTest' --tests 'com.daengs.app.walk.sync.WalkDeliveryTest'
```

53개 통과. 응답 유실 후 재실행, 전송 중 추가/삭제, 다른 계정/서버 세션/게시자 응답,
비활성 서버, 사진 0개와 복원 빈 목록, 기존 Room 데이터와 전송 순서를 확인했다.
전체 로컬 테스트·실기기 CameraX/실서버 전송은 실행하지 않았다.
