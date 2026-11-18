package com.example.focuswear

import com.example.focuswear.routine.ConfigReceipt
import com.example.focuswear.routine.ConfigRequest
import com.example.focuswear.routine.RoutineConfig
import com.example.focuswear.routine.SessionResult
import com.example.focuswear.routine.receiptAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val id = "5be1b84b-7445-4800-9740-eeabf81c7ca2"
    private val start = 10_000L
    private val minute = 60_000L

    @Test
    fun oneSetFinishesAtTheFirstStudyBoundary() {
        val session = session(sets = 1)
        assertEquals(session, session.advance(start + minute - 1).session)
        val update = session.advance(start + minute)
        assertNull(update.session)
        assertEquals(start + minute, update.boundary)
        assertEquals(1, update.result?.completedSets)
        assertEquals(minute, update.result?.studyMillis)
        assertTrue(update.result!!.completed)
    }

    @Test
    fun multipleSetsAlternateAndSkipTheFinalRest() {
        val initial = session(sets = 3)
        val firstRest = initial.advance(start + minute).session!!
        assertEquals(Phase.REST, firstRest.phase)
        assertEquals(1, firstRest.setsCompleted)
        val secondStudy = firstRest.advance(start + 2 * minute).session!!
        assertEquals(Phase.STUDY, secondStudy.phase)
        val secondRest = secondStudy.advance(start + 3 * minute).session!!
        assertEquals(Phase.REST, secondRest.phase)
        val thirdStudy = secondRest.advance(start + 4 * minute).session!!
        assertEquals(Phase.STUDY, thirdStudy.phase)
        val completed = thirdStudy.advance(start + 5 * minute)
        assertNull(completed.session)
        assertEquals(3 * minute, completed.result?.studyMillis)
        assertEquals(completed, initial.advance(start + 9 * minute))
    }

    @Test
    fun pausedStudyAndRestDoNotConsumeTime() {
        val initial = session(sets = 2)
        val pause = initial.togglePause(start + 12_000)
        assertEquals(48_000L, pause.remaining(start + 6 * minute))
        assertEquals(pause, pause.advance(start + 6 * minute).session)
        val resumed = pause.togglePause(start + 6 * minute)
        val restStart = start + 6 * minute + 48_000
        val rest = resumed.advance(restStart).session!!
        val restPaused = rest.togglePause(restStart + 20_000)
        assertEquals(40_000L, restPaused.remaining(restStart + 6 * minute))
        val restResumed = restPaused.togglePause(restStart + 6 * minute)
        assertEquals(Phase.STUDY, restResumed.advance(restStart + 6 * minute + 40_000).session?.phase)
    }

    @Test
    fun earlyFinishKeepsPartialStudyAndCompletedSets() {
        val initial = session(sets = 2)
        val rest = initial.advance(start + 80_000).session!!
        assertEquals(minute, rest.result(start + 80_000).studyMillis)
        val studying = initial.advance(start + 120_000).session!!
        val paused = studying.togglePause(start + 135_250)
        val result = paused.result(start + 180_000)
        assertFalse(result.completed)
        assertEquals(1, result.completedSets)
        assertEquals(75_250L, result.studyMillis)
        assertEquals(result, SessionResult.decode(result.encode()))
    }

    @Test
    fun distractionsOnlyWhileStudyingAndLeaveTheDeadlineIntact() {
        val initial = session(sets = 2)
        val distracted = initial.distract(start + 1_000)
        assertEquals(1, distracted.distractions)
        assertEquals(initial.deadline, distracted.deadline)
        val paused = distracted.togglePause(start + 2_000)
        assertEquals(paused, paused.distract(start + 3_000))
        val rest = distracted.advance(start + minute).session!!
        assertEquals(rest, rest.distract(start + minute + 10_000))
    }

    @Test
    fun malformedAndInconsistentPayloadsAreRejected() {
        assertNull(RoutineConfig.decode("0|1|2"))
        assertNull(RoutineConfig.decode("1|61|2"))
        assertNull(RoutineConfig.decode("1|1|13"))
        assertNull(RoutineConfig.decode("1.1|1|2"))
        assertNull(ConfigRequest.decode("invalid|1|1|1|2"))
        assertNull(ConfigRequest.decode("$id|0|1|1|2"))
        assertNull(SessionResult.decode("$id|42|2|119999|0|true|1|1|2"))
        assertNull(SessionResult.decode("$id|42|1|60000|-1|false|1|1|2"))
        assertNull(SessionResult.decode("$id|42|1|120000|0|false|1|1|2"))
        assertNull(Session.decode("incomplete"))
    }

    @Test
    fun acceptingConfigurationDuringSessionOnlyAffectsTheNextStart() {
        val active = session(sets = 2)
        val watch = WatchState(active.config, false, active, null, false)
        val request = ConfigRequest(id, 2, RoutineConfig(3, 4, 5))
        val updated = watch.acceptConfig(request)
        assertEquals(active, updated.session)
        assertEquals(request.config, updated.nextConfig)
        assertTrue(updated.configReceived)
    }

    @Test
    fun duplicateAndOutOfOrderRequestsStayIdempotentAfterRecovery() {
        val first = ConfigRequest(id, 1, RoutineConfig(1, 1, 2))
        val latest = first.copy(revision = 2, config = RoutineConfig(2, 1, 2))
        assertEquals(ConfigReceipt.NEW, first.receiptAfter(null))
        assertEquals(ConfigReceipt.NEW, latest.receiptAfter(first))
        val persisted = ConfigRequest.decode(latest.encode())
        assertEquals(ConfigReceipt.DUPLICATE, latest.receiptAfter(persisted))
        assertEquals(ConfigReceipt.STALE, first.receiptAfter(persisted))
        assertEquals(ConfigReceipt.STALE, latest.copy(config = first.config).receiptAfter(persisted))
    }

    @Test
    fun sameBootCatchesUpAndNewBootPausesAtTheSavedCheckpoint() {
        val initial = session(sets = 2).distract(start + 18_000)
        val restored = Session.decode(initial.encode())!!
        assertEquals(initial, restored)
        assertEquals(Phase.REST, restored.advance(start + 100_000).session?.phase)
        val rebooted = restored.afterReboot(bootCount = 2)
        assertTrue(rebooted.paused)
        assertEquals(42_000L, rebooted.remaining(50_000))
        assertEquals(1, rebooted.distractions)
        assertNull(rebooted.advance(300_000).boundary)
        assertEquals(92_000L, rebooted.togglePause(50_000).deadline)
    }

    private fun session(sets: Int) =
        Session(id, 1_700_000_000_000, RoutineConfig(1, 1, sets), 1, deadline = start + minute)
}
