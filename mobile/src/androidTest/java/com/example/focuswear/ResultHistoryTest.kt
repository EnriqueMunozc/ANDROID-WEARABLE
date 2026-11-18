package com.example.focuswear

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.routine.SessionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ResultHistoryTest {
    @Test
    fun resultReplayedAfterReconnectIsStoredOnceAcrossDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "results-test-${UUID.randomUUID()}.db"
        val result = SessionResult(
            UUID.randomUUID().toString(), 1_700_000_000_000, RoutineConfig(1, 1, 2),
            1, 73_000, 2, false
        )
        try {
            ResultHistory(context, name).use { first ->
                assertTrue(first.save(result))
            }
            ResultHistory(context, name).use { reopened ->
                val replay = SessionResult.decode(result.encode())!!
                assertFalse(reopened.save(replay))
                assertEquals(listOf(result), reopened.list())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
