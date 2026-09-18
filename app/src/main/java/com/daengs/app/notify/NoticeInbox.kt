package com.daengs.app.notify

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱이 띄운 알림의 목록. **종 아이콘이 여는 것이 이것이다.**
 *
 * ### 왜 안드로이드 알림 그림자로는 모자란가
 *
 * 종을 눌러 시스템 알림 설정으로 보내 봤더니 사용자가 바로 짚었다 — *"여기에 알림이
 * 있으면 불이 들어오고 그 내용이 떠야하는거 아니야?"*. 맞는 말이다. **종은 「설정」이
 * 아니라 「알림 목록」이라는 뜻**이고, 시스템 그림자에 있던 알림은 한 번 밀어 내면
 * 다시 볼 곳이 없다.
 *
 * ### 권한이 꺼져 있어도 여기엔 남는다
 *
 * [postDaengsNotice] 는 **목록에 먼저 적고 나서** 시스템 알림을 띄운다. 그래서 알림
 * 권한을 안 줬거나 채널을 껐어도 **앱 안에서는 볼 수 있다.** 이 순서가 뒤집히면
 * "알림을 껐으니 아무것도 없다" 가 되는데, 그건 사용자가 끈 것(시스템이 부르지 마라)
 * 보다 넓은 해석이다.
 *
 * ### 저장은 SharedPreferences 다
 *
 * Room 이 아니다. 최근 [CAPACITY] 개만 들고 있고 조회 조건도 없어서 표가 할 일이 없다.
 * 이 저장소는 그런 값을 prefs 로 둔다 (`RoomStore` · `PhotoRevealLog` · `GaitTitleStore`).
 * 알림함이 커지면(읽음 상태로 거르기, 무한 스크롤) 그때 표로 옮긴다.
 */
object NoticeInbox {

    /** 몇 개까지 들고 있나. 넘으면 오래된 것이 빠진다. */
    const val CAPACITY = 30

    private val state = MutableStateFlow<List<DaengsNotice>>(emptyList())

    /** 이 프로세스에서 디스크를 한 번이라도 읽었나. [add] 가 빈 목록에 덮어쓰지 않게 본다. */
    @Volatile
    private var loaded = false

    /** 최근 것이 앞이다. 안 읽은 것이 있으면 종에 불이 들어온다. */
    val notices: StateFlow<List<DaengsNotice>> = state

    /** 프로세스가 시작할 때 한 번. `DaengsApp.onCreate` 가 부른다. */
    fun load(context: Context) {
        state.value = read(prefs(context))
        loaded = true
    }

    /**
     * 목록에 적는다.
     *
     * **같은 [DaengsNotice.id] 는 겹쳐 쓴다** — 시스템 알림과 같은 규칙이다. 같은
     * 산책의 일기가 두 번 준비돼도 줄이 둘이 되지 않는다.
     */
    fun add(context: Context, notice: DaengsNotice) {
        if (!loaded) load(context)
        val kept = state.value.filterNot { it.id == notice.id }
        val next = (listOf(notice) + kept).take(CAPACITY)
        state.value = next
        write(prefs(context), next)
    }

    /** 목록을 열었다. 안 읽은 것이 사라지고 종의 불이 꺼진다. */
    fun markAllRead(context: Context) {
        if (state.value.none { !it.read }) return
        val next = state.value.map { if (it.read) it else it.copy(read = true) }
        state.value = next
        write(prefs(context), next)
    }

    /** 사용자가 목록을 비웠다. */
    fun clear(context: Context) {
        state.value = emptyList()
        write(prefs(context), emptyList())
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(prefs: SharedPreferences): List<DaengsNotice> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        // 형식이 바뀌었거나 깨졌으면 **버린다.** 알림 목록은 되살릴 값이 아니다.
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            runCatching { DaengsNotice.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }

    private fun write(prefs: SharedPreferences, notices: List<DaengsNotice>) {
        val array = JSONArray()
        notices.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private const val PREFS = "notice-inbox"
    private const val KEY = "notices"
}

/**
 * 목록에 남는 알림 한 줄.
 *
 * @param id 알림 자리. 시스템 알림과 **같은 값**이라, 겹쳐 쓰는 규칙이 둘에서 같게 돈다.
 * @param extras 눌렀을 때 어디로 갈지. 알림을 눌러 들어오는 길과 **같은 값**이다 —
 *   `MainActivity` 가 한 군데서 해석한다.
 */
data class DaengsNotice(
    val id: Int,
    val channelId: String,
    val title: String,
    val text: String,
    val atMillis: Long,
    val extras: Map<String, String> = emptyMap(),
    val read: Boolean = false,
) {
    internal fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("channel", channelId)
        .put("title", title)
        .put("text", text)
        .put("at", atMillis)
        .put("read", read)
        .put("extras", JSONObject(extras.toMap()))

    internal companion object {
        fun fromJson(json: JSONObject): DaengsNotice {
            val extras = json.optJSONObject("extras")
            return DaengsNotice(
                id = json.getInt("id"),
                channelId = json.getString("channel"),
                title = json.getString("title"),
                text = json.getString("text"),
                atMillis = json.getLong("at"),
                extras = extras?.keys()?.asSequence()?.associateWith { extras.getString(it) }.orEmpty(),
                read = json.optBoolean("read", false),
            )
        }
    }
}
