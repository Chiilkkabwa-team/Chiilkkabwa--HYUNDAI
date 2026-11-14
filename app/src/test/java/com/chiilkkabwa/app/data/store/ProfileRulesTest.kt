package com.chiilkkabwa.app.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** C02: 재실행·전환·삭제 때 어느 프로필이 열리는지. 다른 가족의 기록이 섞여 보이면 안 된다 */
class ProfileRulesTest {
    private val me = Profile("a", "나")
    private val f1 = Profile("b", "가족 1")

    @Test fun relaunch_opens_last_used_profile_not_first() =
        assertEquals("b", PracticeStore.pick(listOf(me, f1), "b").id)

    @Test fun deleted_or_unknown_active_falls_back_to_first() =
        assertEquals("a", PracticeStore.pick(listOf(me, f1), "gone").id)

    @Test fun no_profiles_starts_fresh_default() {
        val p = PracticeStore.pick(emptyList(), null)
        assertEquals("나", p.displayName); assertEquals(0, p.history.reviewsSaved)
    }

    @Test fun new_profile_takes_next_unused_alias() =
        assertEquals("가족 2", PracticeStore.newProfile(listOf(me, f1), 5)!!.displayName)

    @Test fun at_most_four_profiles() =
        assertNull(PracticeStore.newProfile(PracticeStore.ALIASES.mapIndexed { i, a -> Profile("$i", a) }, 5))
}
