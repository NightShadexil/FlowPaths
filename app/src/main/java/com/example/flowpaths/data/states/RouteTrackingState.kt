package com.example.flowpaths.data.states

sealed class RouteTrackingState {
    object Idle : RouteTrackingState()
    object RouteGenerated : RouteTrackingState()
    object TrackingActive : RouteTrackingState()
    object TrackingCompleted : RouteTrackingState()
}