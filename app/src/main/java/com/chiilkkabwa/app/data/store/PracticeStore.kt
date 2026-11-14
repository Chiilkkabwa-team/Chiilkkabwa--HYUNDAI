package com.chiilkkabwa.app.data.store

import android.content.Context
import com.chiilkkabwa.app.core.achievement.Badge
import com.chiilkkabwa.app.core.achievement.EarnedBadge
import com.chiilkkabwa.app.core.achievement.PracticeHistory
import com.chiilkkabwa.app.core.mission.MissionId
import com.chiilkkabwa.app.core.mission.MissionResult
import com.chiilkkabwa.app.core.signal.SignalSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * 기기 내 저장(서버·계정 없음). 프로필(가족 공유 차량) 단위로 분리, 프로필별 삭제(T10).
 * 저장 항목: 미션 결과(출처 포함), 횟수 카운터, 배지, 다음 목표, 복기 기록.
 * 저장하지 않는 것: 위치, VIN, 실명, 주행 경로, 주행 중 행동 데이터.
 */
data class ReviewRecord(val atMs: Long, val durationMin: Int, val distanceKm: Float?, val learnerChip: String?, val mentorChip: String?, val nextGoal: String?)

data class Profile(
    val id: String,
    val displayName: String,          // 기기 안에서만 쓰는 별칭(예: '첫째')
    val history: PracticeHistory = PracticeHistory(),
    val nextGoal: String? = null,
    val reviews: List<ReviewRecord> = emptyList(),
)

class PracticeStore(context: Context) {
    private val prefs = context.getSharedPreferences("chiilkkabwa_store", Context.MODE_PRIVATE)

    fun profiles(): List<Profile> {
        val arr = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun save(p: Profile) {
        val list = profiles().filterNot { it.id == p.id } + p
        prefs.edit().putString(KEY, JSONArray(list.map { toJson(it) }).toString()).apply()
    }

    fun delete(id: String) {
        val list = profiles().filterNot { it.id == id }
        prefs.edit().putString(KEY, JSONArray(list.map { toJson(it) }).toString()).apply()
    }

    /** 마지막으로 쓴 프로필. 재실행해도 다른 가족의 기록이 뜨지 않게(C02) */
    fun activeId(): String? = prefs.getString(ACTIVE, null)
    fun setActive(id: String) = prefs.edit().putString(ACTIVE, id).apply()

    private fun toJson(p: Profile) = JSONObject().apply {
        put("id", p.id); put("name", p.displayName); put("nextGoal", p.nextGoal ?: JSONObject.NULL)
        val h = p.history
        put("results", JSONArray(h.missionResults.map { JSONObject().put("id", it.id.name).put("src", it.source.name).put("at", it.completedAtMs).put("m", it.metric ?: JSONObject.NULL) }))
        put("parking", h.parkingSimCompleted); put("pacts", h.pactsMade); put("reviews", h.reviewsSaved)
        put("grad", h.graduated); put("mentor", h.mentorSessions)
        put("badges", JSONArray(h.badges.map { JSONObject().put("b", it.badge.name).put("at", it.atMs).put("mock", it.mockOnly) }))
        put("reviewLog", JSONArray(p.reviews.map {
            JSONObject().put("at", it.atMs).put("min", it.durationMin).put("km", it.distanceKm ?: JSONObject.NULL)
                .put("l", it.learnerChip ?: JSONObject.NULL).put("m", it.mentorChip ?: JSONObject.NULL).put("g", it.nextGoal ?: JSONObject.NULL)
        }))
    }

    private fun fromJson(o: JSONObject): Profile {
        val res = o.optJSONArray("results") ?: JSONArray()
        val badges = o.optJSONArray("badges") ?: JSONArray()
        val log = o.optJSONArray("reviewLog") ?: JSONArray()
        return Profile(
            id = o.getString("id"),
            displayName = o.optString("name", "나"),
            nextGoal = o.optString("nextGoal").takeIf { it.isNotEmpty() && it != "null" },
            history = PracticeHistory(
                // 모르는 미션·출처 이름(다른 버전에서 저장 등)은 그 항목만 건너뛴다. 예외로 앱 시작이 죽지 않게
                missionResults = (0 until res.length()).mapNotNull { i -> runCatching { res.getJSONObject(i).let {
                    MissionResult(MissionId.valueOf(it.getString("id")), SignalSource.valueOf(it.getString("src")), it.getLong("at"), if (it.isNull("m")) null else it.getDouble("m").toFloat())
                } }.getOrNull() },
                parkingSimCompleted = o.optInt("parking"), pactsMade = o.optInt("pacts"), reviewsSaved = o.optInt("reviews"),
                graduated = o.optBoolean("grad"), mentorSessions = o.optInt("mentor"),
                badges = (0 until badges.length()).mapNotNull { i -> runCatching { badges.getJSONObject(i).let { EarnedBadge(Badge.valueOf(it.getString("b")), it.getLong("at"), it.getBoolean("mock")) } }.getOrNull() },
            ),
            reviews = (0 until log.length()).map { i -> log.getJSONObject(i).let {
                ReviewRecord(it.getLong("at"), it.getInt("min"), if (it.isNull("km")) null else it.getDouble("km").toFloat(),
                    it.optString("l").takeIf { s -> s.isNotEmpty() && s != "null" }, it.optString("m").takeIf { s -> s.isNotEmpty() && s != "null" }, it.optString("g").takeIf { s -> s.isNotEmpty() && s != "null" })
            } },
        )
    }

    companion object {
        private const val KEY = "profiles_v1"
        private const val ACTIVE = "active_profile_v1"

        /** 기기 안에서만 쓰는 별칭. 글자 입력 없이 고른다(UI 규칙: 텍스트 입력 없음) */
        val ALIASES = listOf("나", "가족 1", "가족 2", "가족 3")

        /** 열 프로필: 저장된 활성 프로필 → 없으면 첫 프로필 → 없으면 새 '나' */
        fun pick(list: List<Profile>, activeId: String?): Profile =
            list.firstOrNull { it.id == activeId } ?: list.firstOrNull() ?: Profile("default", ALIASES[0])

        /** 새 프로필: 아직 안 쓴 별칭. 다 쓰면 null(최대 4명) */
        fun newProfile(list: List<Profile>, nowMs: Long): Profile? =
            ALIASES.firstOrNull { a -> list.none { it.displayName == a } }?.let { Profile("p$nowMs", it) }
    }
}
