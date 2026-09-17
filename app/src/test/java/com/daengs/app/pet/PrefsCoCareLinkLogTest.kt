package com.daengs.app.pet

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 연결 기록이 **기기에 남는지** (#440).
 *
 * 내보내기는 주보호자가 다른 폰에서 한다. 내보내진 사람은 앱을 다시 켠 뒤 목록을 받으며 알게
 * 되므로, 메모리에만 두면 그 경우를 영영 못 잡는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrefsCoCareLinkLogTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun pet(id: String, isGroupOwner: Boolean) = Pet(
        id = id,
        name = "테스트연결",
        breed = "mix",
        sex = null,
        neutered = null,
        weightKg = null,
        birthDate = null,
        birthDateKind = null,
        isPrimary = true,
        isOwner = true,
        isGroupOwner = isGroupOwner,
    )

    private fun holderFor(account: String, watch: CoCareEndWatch, vararg pets: Pet) = PetHolder(
        listPets = { Result.success(PetList(pets.toList(), 5)) },
        currentAccount = { account },
        onListed = watch::observe,
    )

    @Test
    fun `앱을 다시 켜도 내보내진 것을 알아챈다`() = runTest {
        // 첫 실행: 연결된 채로 목록을 받는다.
        val before = CoCareEndWatch(PrefsCoCareLinkLog(context))
        holderFor("me", before, pet("p1", isGroupOwner = false)).refresh("token")
        assertNull(before.pending)

        // 다른 폰에서 내보내졌다. 새 프로세스라 홀더도 기록도 새로 만든다.
        val after = CoCareEndWatch(PrefsCoCareLinkLog(context))
        holderFor("me", after, pet("p1", isGroupOwner = true)).refresh("token")
        assertEquals(CoCareEndWatch.Pending("me", setOf("p1")), after.pending)

        // 띄우고 또 다시 켜면 안 뜬다.
        after.shown()
        val third = CoCareEndWatch(PrefsCoCareLinkLog(context))
        holderFor("me", third, pet("p1", isGroupOwner = true)).refresh("token")
        assertNull(third.pending)
    }

    @Test
    fun `계정마다 따로 적는다`() = runTest {
        val log = PrefsCoCareLinkLog(context)
        CoCareEndWatch(log).observe("a", listOf(pet("p1", isGroupOwner = false)))

        val other = CoCareEndWatch(PrefsCoCareLinkLog(context))
        holderFor("b", other, pet("p1", isGroupOwner = true)).refresh("token")
        assertNull("B 에게 A 의 알림이 뜨면 안 된다", other.pending)
        assertEquals(setOf("p1"), PrefsCoCareLinkLog(context).load("a"))
        assertEquals(emptySet<String>(), PrefsCoCareLinkLog(context).load("b"))
    }

    @Test
    fun `탈퇴하면 그 계정 기록만 지운다`() {
        val log = PrefsCoCareLinkLog(context)
        log.save("a", setOf("p1", "p2"))
        log.save("b", setOf("p3"))
        log.forget("a")
        assertEquals(emptySet<String>(), PrefsCoCareLinkLog(context).load("a"))
        assertEquals(setOf("p3"), PrefsCoCareLinkLog(context).load("b"))
    }
}
