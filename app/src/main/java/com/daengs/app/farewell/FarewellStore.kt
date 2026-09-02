package com.daengs.app.farewell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/**
 * 아이를 배웅한 날을 적어 둔다.
 *
 * **삭제와는 다른 일이다.** 목록에서 지우는 것은 없던 일로 만드는 것이고, 배웅은
 * 있었던 일을 적어 두는 것이다. 그래서 배웅한 아이는 목록에 남는다.
 *
 * **⚠️ 서버에 이 칸이 없다.** `pets` 표에 "간 날" 이 없어서 지금은 기기에만 있다.
 * 기기를 바꾸거나 앱을 지우면 사라진다는 뜻이라 **오래 둘 자리가 아니다** — 아이를
 * 잃은 기록이 그렇게 사라지면 안 된다. 서버에 칸이 생기면 이 클래스만 갈아끼운다.
 *
 * SharedPreferences 를 쓰는 이유는 `RoomStore` 와 같다 — 적을 것이 아이마다 날짜 하나뿐이고,
 * 합성 시점에 바로 읽어야 목록이 깜빡이지 않는다.
 */
class FarewellStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 배웅한 날. 아직 아니면 null 이다. */
    fun dayOf(petId: String): LocalDate? =
        prefs.getLong(petId, 0L).takeIf { it > 0L }?.let(LocalDate::ofEpochDay)

    fun sendOff(petId: String, day: LocalDate) {
        prefs.edit().putLong(petId, day.toEpochDay()).apply()
    }

    /** 잘못 눌렀을 때 되돌린다. **되돌릴 길이 없으면 아무도 안 누른다.** */
    fun undo(petId: String) {
        prefs.edit().remove(petId).apply()
    }

    /** 탈퇴할 때. 서버에 사본이 없으므로 여기서 지우면 정말 사라진다. */
    fun forgetEverything() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "farewell"
    }
}

@Composable
fun rememberFarewellStore(): FarewellStore {
    val context = LocalContext.current
    return remember(context) { FarewellStore(context) }
}
