package com.daengs.app.territory

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

sealed interface TerritoryFailure {
    data object Offline : TerritoryFailure
    data object TimedOut : TerritoryFailure
    data object UnsupportedLocation : TerritoryFailure
    data object Server : TerritoryFailure
    data object InvalidResponse : TerritoryFailure
    data object Unknown : TerritoryFailure
}

fun Throwable.toTerritoryFailure(): TerritoryFailure = when (this) {
    is UnknownHostException -> TerritoryFailure.Offline
    is SocketTimeoutException -> TerritoryFailure.TimedOut
    is IllegalArgumentException -> TerritoryFailure.UnsupportedLocation
    is SerializationException, is NoSuchElementException -> TerritoryFailure.InvalidResponse
    is TerritorySiteApiException -> if (status >= 500) {
        TerritoryFailure.Server
    } else {
        TerritoryFailure.InvalidResponse
    }
    else -> TerritoryFailure.Unknown
}

fun TerritoryFailure.userMessage(): String = when (this) {
    TerritoryFailure.Offline -> "인터넷 연결을 확인해 주세요."
    TerritoryFailure.TimedOut -> "점령지를 불러오는 데 시간이 오래 걸리고 있어요."
    TerritoryFailure.UnsupportedLocation -> "아직 점령지를 제공하지 않는 지역이에요."
    TerritoryFailure.Server -> "점령지 서버가 잠시 응답하지 않아요."
    TerritoryFailure.InvalidResponse -> "점령지 정보를 읽지 못했어요."
    TerritoryFailure.Unknown -> "점령지를 불러오지 못했어요."
}
