package com.daengs.app.dogcard.store

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 버전 1 에서 올라가도 **뽑아 둔 카드가 그대로 남는지.**
 *
 * 이 DB 에는 사용자가 모은 도감이 들어 있고 **서버에 사본이 없다.** 마이그레이션이
 * 잘못되면 앱 업데이트 한 번에 통째로 사라지는데, 되돌릴 방법이 없다.
 *
 * `MigrationTestHelper` 대신 손으로 옛 DB 를 만든다 — 스키마 json 을 test 소스셋의
 * 에셋으로 끌어오는 설정을 더하지 않으려고 그런다 (`WalkMigrationTest` 와 같다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CardMigrationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getDatabasePath(CardDatabase.NAME).also { it.parentFile?.mkdirs() }.delete()
    }

    @Test
    fun `1에서 2로 올라가도 뽑아 둔 카드가 남는다`() = runBlocking {
        legacyV1 {
            execSQL(
                "INSERT INTO drawn_card VALUES " +
                    "('c1','u1','cabbage','d1','몽이',1000,'DG-0412',10,20,110,140)," +
                    "('c2',NULL,'tomato',NULL,'우리 아이',2000,'DG-0824',0,0,0,0)",
            )
        }

        val db = openLatest()
        // **최근이 앞이고, 둘러보기로 뽑은 카드도 같이 온다** — 아직 누구 것도 아닌
        // 카드라 지금 보는 사람의 것으로 친다 (`CardDao.forUser`).
        val rows = db.cardDao().forUser("u1")
        assertEquals(listOf("c2", "c1"), rows.map { it.id })

        // 얼굴 자리가 사라지면 다음에 열 때 카드 구멍이 빈다.
        val mine = rows.first { it.id == "c1" }
        assertEquals(10, mine.coreLeft)
        assertEquals(140, mine.coreBottom)
        assertEquals("DG-0412", mine.codeText)
        assertEquals("몽이", mine.dogName)

        // 로그인 없이 뽑아 둔 카드는 여전히 주인이 없다. 도장은 로그인 때 찍힌다.
        assertEquals(listOf("c2"), db.cardDao().orphans().map { it.id })

        db.close()
    }

    /**
     * **옛 카드는 `userFramed = 0` 이다.**
     *
     * 그 카드들은 정말로 앱이 알아서 구멍에 끼운 것이고, 예전 규칙(1.15배 확대 +
     * 턱걸이)으로 계속 그려야 한다 — 이미 뽑아 둔 카드가 업데이트로 달라지면 안 된다.
     */
    @Test
    fun `옛 카드는 사용자가 맞춘 것으로 치지 않는다`() = runBlocking {
        legacyV1 {
            execSQL(
                "INSERT INTO drawn_card VALUES " +
                    "('c1','u1','cabbage','d1','몽이',1000,'DG-0412',10,20,110,140)",
            )
        }

        val db = openLatest()
        assertFalse(db.cardDao().forUser("u1")[0].userFramed)
        db.close()
    }

    /** 버전 1 짜리 DB 파일을 손으로 만든다. 스키마는 `app/schemas/.../1.json` 그대로다. */
    private fun legacyV1(fill: SQLiteDatabase.() -> Unit) {
        val legacy = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(CardDatabase.NAME),
            null,
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `drawn_card` (`id` TEXT NOT NULL, `appUserId` TEXT, " +
                "`templateId` TEXT NOT NULL, `dogId` TEXT, `dogName` TEXT NOT NULL, " +
                "`drawnAtMillis` INTEGER NOT NULL, `codeText` TEXT NOT NULL, " +
                "`coreLeft` INTEGER NOT NULL, `coreTop` INTEGER NOT NULL, " +
                "`coreRight` INTEGER NOT NULL, `coreBottom` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_drawn_card_templateId` ON `drawn_card` (`templateId`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_drawn_card_dogId` ON `drawn_card` (`dogId`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_drawn_card_drawnAtMillis` ON `drawn_card` (`drawnAtMillis`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_drawn_card_appUserId` ON `drawn_card` (`appUserId`)")
        legacy.fill()
        legacy.version = 1
        legacy.close()
    }

    private fun openLatest(): CardDatabase =
        Room.databaseBuilder(context, CardDatabase::class.java, CardDatabase.NAME)
            .addMigrations(CardDatabase.MIGRATION_1_2)
            .build()
}
