package com.chiilkkabwa.app.data.navigation

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.chiilkkabwa.app.core.navigation.*
import com.chiilkkabwa.app.data.vehicle.AaosVehicleSource
import com.chiilkkabwa.app.data.vehicle.VehicleRepository

/** Not exported; only vehicleDebug. Never launched by MainActivity or Intent extras. */
class NaviProbeActivity : Activity() {
    internal lateinit var owner: NaviProbeOwner
    private val main = Handler(Looper.getMainLooper())
    private var repository: VehicleRepository? = null
    private lateinit var text: TextView
    private lateinit var beginButton: Button
    private var resumed = false
    private var lastReport = ""
    internal val traces = mutableListOf<NaviClientTrace>()
    private val poller = object : Runnable {
        override fun run() {
            if (!resumed) return
            repository?.snapshot?.value?.let(owner::updateSafety)
            owner.poll()
            render()
            if (owner.mustCloseView) { closeProbe(); finish(); return }
            main.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = NaviHelperClient.actualRuntime()
        val adapter = createDebugNaviAdapter(this) { stage ->
            traces += stage
            if (traces.size > 64) traces.removeAt(0)
            Log.i("A03_PROBE", "SDK_TRACE=" + stage.name)
        }
        owner = NaviProbeOwner(runtime, adapter, {
            checkSelfPermission(NaviHelperClient.ROUTE_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }, { System.currentTimeMillis() })
        text = TextView(this).apply { textSize = 16f }
        beginButton = Button(this).apply {
            text = "SDK 초기화 — 개발자 명시 실행"
            setOnClickListener { explicitInitialize() }
        }
        val stop = Button(this).apply {
            text = "종료"
            setOnClickListener { finish() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text); addView(beginButton); addView(stop)
        })
        if (runtime.allowed) repository = VehicleRepository(listOf(AaosVehicleSource(this)))
        // No NaviHelper initialize onCreate/onResume. Off/physical devices do not even subscribe.
        render()
        if (!runtime.allowed) {
            render()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        owner.onResume()
        repository?.start()
        main.post(poller)
    }

    override fun onPause() {
        closeProbe()
        super.onPause()
    }

    override fun onDestroy() {
        closeProbe()
        super.onDestroy()
    }

    internal fun explicitInitialize(): Boolean {
        repository?.snapshot?.value?.let(owner::updateSafety)
        val started = owner.begin(true)
        render()
        return started
    }

    internal fun explicitRequest(route: PracticeRouteRequest?): Boolean {
        repository?.snapshot?.value?.let(owner::updateSafety)
        val called = owner.submit(route, true)
        render()
        return called
    }

    internal fun closeProbe() {
        resumed = false
        main.removeCallbacks(poller)
        if (::owner.isInitialized) owner.onPause()
        repository?.stop()
    }

    internal fun report(): String {
        val p = owner.progress
        val s = repository?.snapshot?.value
        // No coordinates/POI/route IDs, account, VIN or Throwable messages.
        return "state=" + owner.event + " reason=" + owner.reason +
            " initializationObserved=" + p.initialized + " callReturned=" + p.callReturned +
            " searchStarted=" + p.searchStarted + " searchCompleted=" + p.searchCompleted +
            " navigationStatus=" + p.navigationStatus + " guidanceConfirmed=" + p.guidanceConfirmed +
            " permission=" + (checkSelfPermission(NaviHelperClient.ROUTE_PERMISSION) == PackageManager.PERMISSION_GRANTED) +
            " signalSource=" + p.signalSource +
            " gear=" + s?.gear?.value + "/" + s?.gear?.status + "/" + s?.gear?.source + "@" + s?.gear?.receivedAtMs +
            " parkingBrake=" + s?.parkingBrakeOn?.value + "/" + s?.parkingBrakeOn?.status + "/" + s?.parkingBrakeOn?.source + "@" + s?.parkingBrakeOn?.receivedAtMs +
            " ux=" + s?.uxRestricted?.value + "/" + s?.uxRestricted?.status + "/" + s?.uxRestricted?.source + "@" + s?.uxRestricted?.receivedAtMs +
            " cleanupFailed=" + p.cleanupFailed
    }

    private fun render() {
        val report = report()
        text.text = "A03 Debug 시험 — 실제 안내 성공 미확정\n" + report
        beginButton.isEnabled = resumed && owner.initializationBlock() == null
        if (report != lastReport) { Log.i("A03_PROBE", report); lastReport = report }
    }
}
