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

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_THEME = "theme"
    }
}

@Composable
fun rememberRoomStore(): RoomStore {
    val context = LocalContext.current
    return remember(context) { RoomStore(context) }
}
