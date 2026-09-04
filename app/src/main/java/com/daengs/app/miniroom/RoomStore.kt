package com.daengs.app.miniroom

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 방 배치와 테마를 기기에 저장한다.
 *
 * **왜 DataStore 가 아니라 SharedPreferences 인가**
 * - 저장할 게 한 줄짜리 문자열 두 개뿐이다
 * - DataStore 는 읽기가 비동기(Flow)라 첫 프레임에 방이 **빈 채로 한 번 그려졌다가**
 *   채워진다. 깜빡임이 눈에 띈다. 여기서는 합성 시점에 바로 읽는 편이 낫다
 * - 의존성이 안 늘어난다
 *
 * 나중에 서버나 DataStore 로 옮길 때는 이 클래스만 갈아끼우면 된다.
 * 직렬화는 [RoomCodec] 이 따로 들고 있어서 저장소를 바꿔도 형식은 그대로다.
 */
class RoomStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("miniroom", Context.MODE_PRIVATE)

    fun loadItems(): List<PlacedItem>? = RoomCodec.decode(prefs.getString(KEY_ITEMS, null))

    fun saveItems(items: List<PlacedItem>) {
        prefs.edit().putString(KEY_ITEMS, RoomCodec.encode(items)).apply()
    }

    /**
     * 액자에 건 카드. null 이면 아직 안 골랐다 — 그때는 발자국이 걸린다.
     *
     * **카드 id 만 든다.** 방은 그 문자열이 무엇을 뜻하는지 모른다. 카드가 지워졌으면
     * 못 찾을 뿐이고, 그때도 발자국으로 돌아간다.
     */
    fun loadFrameCardId(): String? = prefs.getString(KEY_FRAME, null)

    fun saveFrameCardId(id: String?) {
        prefs.edit().apply { if (id == null) remove(KEY_FRAME) else putString(KEY_FRAME, id) }.apply()
    }

    fun loadThemeId(): String? = prefs.getString(KEY_THEME, null)

    fun saveThemeId(id: String) {
        prefs.edit().putString(KEY_THEME, id).apply()
    }

    /**
     * 방을 통째로 잊는다. **회원 탈퇴에서만 쓴다.**
     *
     * 방 배치는 서버에 사본이 없어서 이 기기에만 있다. 안 지우면 다음에 이 폰으로
     * 로그인한 사람이 남이 꾸며 둔 방을 물려받는다. 로그아웃은 "잠깐 나감" 이라
     * 그대로 두는 게 맞고, 탈퇴는 "흔적을 지움" 이라 다르다.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * 방 둘러보기를 본 적 있나.
     *
     * **기기의 일이지 계정의 일이 아니다.** 서버에 안 올린다 — 같은 사람이 새 폰에서
     * 처음 방을 열면 다시 보는 것이 맞다.
     */
    fun tourSeen(): Boolean = prefs.getBoolean(KEY_TOUR_SEEN, false)

    fun markTourSeen() {
        prefs.edit().putBoolean(KEY_TOUR_SEEN, true).apply()
    }

    /**
     * **방에서 뺀 아이들.**
     *
     * 넣은 아이가 아니라 **뺀 아이를 적는다.** 기본이 "등록한 아이가 다 선다" 라,
     * 넣은 쪽을 적으면 새로 등록한 아이가 방에 안 서고 사용자가 매번 켜 줘야 한다.
     *
     * 서버에 안 올린다 — 방 배치·액자 카드와 같은 처지로 **이 기기의 일**이다.
     */
    fun loadHiddenPetIds(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_PETS, emptySet())?.toSet() ?: emptySet()

    fun saveHiddenPetIds(ids: Set<String>) {
        // `getStringSet` 이 돌려주는 집합은 고치면 안 되는 물건이라 새로 만들어 넣는다.
        prefs.edit().putStringSet(KEY_HIDDEN_PETS, ids.toSet()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_HIDDEN_PETS = "hidden_pets"
        const val KEY_TOUR_SEEN = "tour_seen"
        const val KEY_THEME = "theme"
        const val KEY_FRAME = "frame_card"
    }
}

@Composable
fun rememberRoomStore(): RoomStore {
    val context = LocalContext.current
    return remember(context) { RoomStore(context) }
}
