package com.daengs.app.journey

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import java.net.URI

private const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"

/**
 * 밖으로 나가는 Intent 에는 **서버가 준 네이버 경로 스킴만** 통과시킨다.
 *
 * 서버에서 받은 문자열을 그대로 `startActivity` 에 넘기는 자리라, 검사 없이 두면
 * 엉뚱한 앱이나 주소로 사용자를 보낼 수 있다.
 */
fun isTrustedNaverHandoff(url: String): Boolean {
    val uri = runCatching { URI.create(url) }.getOrNull() ?: return false
    return uri.scheme == "nmap" && uri.host == "route" && uri.path.trimStart('/').substringBefore('/') in
        setOf("walk", "car", "public")
}

fun openNaverHandoff(context: Context, url: String) {
    if (!isTrustedNaverHandoff(url)) {
        Toast.makeText(context, "지원하지 않는 길찾기 링크입니다.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(NAVER_MAP_PACKAGE))
    } catch (_: ActivityNotFoundException) {
        val market = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$NAVER_MAP_PACKAGE".toUri(),
        )
        runCatching { context.startActivity(market) }.onFailure {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$NAVER_MAP_PACKAGE".toUri(),
                ),
            )
        }
    }
}
