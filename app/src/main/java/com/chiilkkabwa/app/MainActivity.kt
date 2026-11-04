package com.chiilkkabwa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chiilkkabwa.app.data.vehicle.AaosVehicleSource
import com.chiilkkabwa.app.ui.ChiilViewModel
import com.chiilkkabwa.app.ui.screens.AppRoot
import com.chiilkkabwa.app.ui.theme.ChiilTheme

/**
 * 단일 Activity. DO(distractionOptimized)를 선언하지 않는다 → 주행 중에는 플랫폼이 앱 사용을 막고,
 * 앱도 CarUxRestrictions로 스스로 잠근다(이중 안전).
 */
class MainActivity : ComponentActivity() {
    private val vm: ChiilViewModel by viewModels()

    private val pedalPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과는 AaosVehicleSource가 재시작 시 확인 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.USE_MOCK) pedalPermission.launch(AaosVehicleSource.PEDAL_PERMISSION) // 페달 미션(조건부)용 위험 권한
        setContent {
            ChiilTheme {
                val ui by vm.ui.collectAsState()
                AppRoot(vm, ui)
            }
        }
    }
}
