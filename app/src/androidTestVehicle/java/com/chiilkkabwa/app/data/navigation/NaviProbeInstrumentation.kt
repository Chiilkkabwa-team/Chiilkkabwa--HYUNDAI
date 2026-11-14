package com.chiilkkabwa.app.data.navigation

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.chiilkkabwa.app.core.navigation.*
import org.json.JSONObject

/**
 * Stock Android instrumentation: no third-party runner dependency.
 * adb shell am instrument -w -e a03 true -e mode initialize <test package>/<runner>
 * Framework requires privileged/shell instrumentation launch; Activity itself is not exported.
 */
class NaviProbeInstrumentation : Instrumentation() {
    private var args = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); args = arguments ?: Bundle(); start() }

    override fun onStart() {
        val result = Bundle()
        if (args.getString("a03") != "true") {
            result.putString("result", "BLOCKED: explicit -e a03 true required")
            finish(Activity.RESULT_CANCELED, result)
            return
        }
        if (!NaviHelperClient.actualRuntime().allowed) {
            result.putString("result", "BLOCKED: debug EMULATOR opt-in required; no Activity or SDK created")
            finish(Activity.RESULT_CANCELED, result)
            return
        }
        var activity: NaviProbeActivity? = null
        try {
            val mode = args.getString("mode") ?: "inspect"
            require(mode in setOf("inspect", "initialize", "request", "lifecycle"))
            fun launch(): NaviProbeActivity =
                startActivitySync(Intent(targetContext, NaviProbeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NaviProbeActivity
            activity = launch()
            val first = activity
            runOnMainSync {
                check(!first.owner.hasAttemptedStart || !NaviHelperClient.actualRuntime().allowed)
                checkpoint("CREATED_NO_AUTO_INITIALIZE", first)
            }
            if (mode != "inspect" && NaviHelperClient.actualRuntime().allowed) {
                waitUntil(3_000) {
                    var eligible = false
                    runOnMainSync { eligible = first.owner.initializationBlock() == null }
                    eligible
                }
                runOnMainSync { first.explicitInitialize(); checkpoint("EXPLICIT_START", first) }
                waitUntil(18_000) {
                    var done = false
                    runOnMainSync { first.owner.poll(); done = first.owner.progress.phase != NaviPhase.INITIALIZING }
                    done
                }
                runOnMainSync { checkpoint("INITIALIZATION_OBSERVATION", first) }
                if (mode == "request") {
                    // No default/synthetic POI. A missing POI cannot invoke an SDK route function.
                    val route = args.getString("routeJson")?.let(::parseRoute)
                    var called = false
                    runOnMainSync { called = first.explicitRequest(route); checkpoint("REQUEST_OBSERVATION", first) }
                    if (called) {
                        waitUntil(18_000) {
                            var done = false
                            runOnMainSync {
                                first.owner.poll()
                                done = first.owner.progress.searchCompleted || first.owner.progress.phase in
                                    setOf(NaviPhase.FAILED, NaviPhase.BLOCKED, NaviPhase.STOPPED)
                            }
                            done
                        }
                        runOnMainSync { checkpoint("SEARCH_RESPONSE_OBSERVATION", first) }
                    }
                }
                if (mode == "lifecycle") {
                    runOnMainSync { first.finish() }
                    waitForIdleSync()
                    runOnMainSync {
                        check(first.owner.event == "RELEASED")
                        checkpoint("FIRST_RELEASED", first)
                    }
                    val second = launch()
                    activity = second
                    runOnMainSync {
                        check(!second.owner.hasAttemptedStart)
                        check(second.traces.isEmpty())
                        checkpoint("REENTRY_NO_AUTO_INITIALIZE", second)
                    }
                    waitUntil(3_000) {
                        var eligible = false
                        runOnMainSync { eligible = second.owner.initializationBlock() == null }
                        eligible
                    }
                    runOnMainSync { second.explicitInitialize() }
                    waitUntil(18_000) {
                        var done = false
                        runOnMainSync { second.owner.poll(); done = second.owner.progress.phase != NaviPhase.INITIALIZING }
                        done
                    }
                    runOnMainSync { checkpoint("SECOND_INITIALIZATION", second) }
                }
            }
            result.putString("result", "PROBE_COMPLETED; SDK/search/guidance outcomes require individual evidence")
        } catch (e: Exception) {
            // User-supplied POIs/coordinates may be in exception messages: do not print them.
            result.putString("error", e.javaClass.simpleName)
        } finally {
            val closing = activity
            if (closing != null) runOnMainSync {
                closing.closeProbe()
                checkpoint("RELEASED", closing)
                closing.finish()
            }
        }
        finish(if (result.containsKey("error")) Activity.RESULT_CANCELED else Activity.RESULT_OK, result)
    }

    private fun checkpoint(label: String, activity: NaviProbeActivity) {
        sendStatus(0, Bundle().apply { putString("stage", label); putLong("atWallMs", System.currentTimeMillis()); putString("observation", activity.report()) })
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val start = SystemClock.elapsedRealtime()
        while (!condition() && SystemClock.elapsedRealtime() - start < timeoutMs) Thread.sleep(100)
    }

    private fun parseRoute(json: String): PracticeRouteRequest {
        require(json.length <= 16_384)
        fun point(p: JSONObject) = RoutePoint(p.getDouble("latitude"), p.getDouble("longitude"),
            p.getString("poiName"), p.getString("poiId"), p.getString("address"),
            p.getString("poiSubId"), p.optString("poiProvider", ""))
        val root = JSONObject(json)
        val points = root.optJSONArray("waypoints")
        require((points?.length() ?: 0) <= 2)
        return PracticeRouteRequest(root.getString("requestId"), point(root.getJSONObject("destination")),
            (0 until (points?.length() ?: 0)).map {
                val item = requireNotNull(points).getJSONObject(it)
                RouteWaypoint(item.getInt("position"), point(item.getJSONObject("point")))
            }, RouteDataSource.USER_SELECTED)
    }
}
