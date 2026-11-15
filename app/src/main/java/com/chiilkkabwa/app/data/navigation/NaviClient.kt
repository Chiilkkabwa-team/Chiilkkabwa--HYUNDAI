package com.chiilkkabwa.app.data.navigation

import com.chiilkkabwa.app.core.navigation.PracticeRouteRequest
import com.chiilkkabwa.app.core.navigation.RouteProblem

/** App-owned port, not extra Pleos SDK methods. All calls/events belong to one execution context. */
interface NaviClient {
    fun connect(sink: (NaviEvent) -> Unit)
    fun request(route: PracticeRouteRequest)
    fun close()
}
sealed interface NaviEvent {
    data object Connected : NaviEvent
    data class InitializationFailed(val problem: RouteProblem) : NaviEvent
    data object SearchStarted : NaviEvent
    data object SearchCompleted : NaviEvent
    data class NavigationObserved(val status: String) : NaviEvent
    data class Error(val code: String) : NaviEvent
}
class NaviClientException(val problem: RouteProblem) : RuntimeException(problem.name)

/** Local tracing hooks, not new SDK APIs. No payload or PII. */
internal enum class NaviClientTrace { SDK_OBJECT_CREATED, INITIALIZE_CALLED, CONNECTED_CALLBACK, FAILED_CALLBACK, LISTENER_REMOVED, RELEASE_RETURNED }
