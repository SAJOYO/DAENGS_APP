package com.daengs.app.map.provider.naver

/**
 * Semantic stack, independent of composition/attachment order and the number of walks.
 * All trace sheets are composed into ground tiles before display; no route belongs between sheets.
 * NAVER uses negative six-digit defaults for ground/path layers. A small negative value such as
 * -100 is ABOVE the default path (-100000), even though both are below map labels (0).
 */
internal object NaverWalkLayerOrder {
    const val TRACE_SHEETS = -300_000
    const val ROUTE = -100_000
}
