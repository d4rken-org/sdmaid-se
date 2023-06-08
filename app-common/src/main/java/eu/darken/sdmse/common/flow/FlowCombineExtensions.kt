package eu.darken.sdmse.common.flow

import kotlinx.coroutines.flow.Flow


//@Suppress("UNCHECKED_CAST", "LongParameterList")
//inline fun <T1, T2, R> combine(
//    flow: Flow<T1>,
//    flow2: Flow<T2>,
//    crossinline transform: suspend (T1, T2) -> R
//): Flow<R> = kotlinx.coroutines.flow.combine(
//    flow,
//    flow2
//) { args: Array<*> ->
//    transform(
//        args[0] as T1,
//        args[1] as T2
//    )
//}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    crossinline transform: suspend (T1, T2, T3) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow,
    flow2,
    flow3,
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    crossinline transform: suspend (T1, T2, T3, T4, T5) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow,
    flow2,
    flow3,
    flow4,
    flow5
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6,
    flow7
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6,
    flow7,
    flow8
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    flow11: Flow<T11>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10, flow11
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
        args[10] as T11
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    flow11: Flow<T11>,
    flow12: Flow<T12>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10, flow11, flow12
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
        args[10] as T11,
        args[11] as T12,
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    flow11: Flow<T11>,
    flow12: Flow<T12>,
    flow13: Flow<T13>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10, flow11, flow12, flow13
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
        args[10] as T11,
        args[11] as T12,
        args[12] as T13,
    )
}


@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    flow11: Flow<T11>,
    flow12: Flow<T12>,
    flow13: Flow<T13>,
    flow14: Flow<T14>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10, flow11, flow12, flow13, flow14
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
        args[10] as T11,
        args[11] as T12,
        args[12] as T13,
        args[13] as T14,
    )
}

@Suppress("UNCHECKED_CAST", "LongParameterList")
inline fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    flow9: Flow<T9>,
    flow10: Flow<T10>,
    flow11: Flow<T11>,
    flow12: Flow<T12>,
    flow13: Flow<T13>,
    flow14: Flow<T14>,
    flow15: Flow<T15>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) -> R
): Flow<R> = kotlinx.coroutines.flow.combine(
    flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8, flow9, flow10, flow11, flow12, flow13, flow14, flow15
) { args: Array<*> ->
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
        args[8] as T9,
        args[9] as T10,
        args[10] as T11,
        args[11] as T12,
        args[12] as T13,
        args[13] as T14,
        args[14] as T15
    )
}
