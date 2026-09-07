package com.daengs.app.place

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

/** 통신 구현의 예외를 화면이 이해할 수 있는 실패 이유로 닫는다. */
sealed interface PlaceFailure {
    data object Offline : PlaceFailure

    data object Timeout : PlaceFailure

    data object RateLimited : PlaceFailure

    data object ServerUnavailable : PlaceFailure

    data class RequestRejected(val status: Int) : PlaceFailure

    data object InvalidResponse : PlaceFailure

    data object UnsupportedLocation : PlaceFailure

    data object NothingToRetry : PlaceFailure

    data object Unknown : PlaceFailure
}

fun PlaceFailure.userMessage(): String = when (this) {
    PlaceFailure.Offline -> "인터넷 연결을 확인해주세요."
    PlaceFailure.Timeout -> "검색이 오래 걸리고 있어요. 다시 시도해주세요."
    PlaceFailure.RateLimited -> "검색 요청이 많아요. 잠시 후 다시 시도해주세요."
    PlaceFailure.ServerUnavailable -> "시설 검색 서버에 잠시 연결할 수 없어요."
    is PlaceFailure.RequestRejected ->
        "검색 요청을 처리하지 못했습니다. 앱을 최신 버전으로 업데이트해주세요."
    PlaceFailure.InvalidResponse -> "시설 정보를 불러오지 못했습니다."
    PlaceFailure.UnsupportedLocation -> "현재는 대한민국 안의 시설만 검색할 수 있어요."
    PlaceFailure.NothingToRetry -> "다시 실행할 장소 검색이 없습니다."
    PlaceFailure.Unknown -> "시설 검색을 처리하지 못했습니다."
}

val PlaceFailure.retryable: Boolean
    get() = when (this) {
        PlaceFailure.Offline,
        PlaceFailure.Timeout,
        PlaceFailure.RateLimited,
        PlaceFailure.ServerUnavailable,
        PlaceFailure.InvalidResponse,
        PlaceFailure.Unknown,
        -> true
        is PlaceFailure.RequestRejected,
        PlaceFailure.UnsupportedLocation,
        PlaceFailure.NothingToRetry,
        -> false
    }

fun Throwable.toPlaceFailure(): PlaceFailure = when (this) {
    is UnknownHostException, is ConnectException -> PlaceFailure.Offline
    is SocketTimeoutException -> PlaceFailure.Timeout
    is PlaceApiException -> when (status) {
        429 -> PlaceFailure.RateLimited
        in 500..599 -> PlaceFailure.ServerUnavailable
        else -> PlaceFailure.RequestRejected(status)
    }
    is SerializationException, is NoSuchElementException, is IllegalArgumentException ->
        PlaceFailure.InvalidResponse
    else -> PlaceFailure.Unknown
}
