package com.daengs.app.map.shell

import com.daengs.app.map.style.WalkRouteAppearance

enum class WalkLayerStack { RECORDS }
data class WalkLayerPresentation(val stack: WalkLayerStack, val route: WalkRouteAppearance)
