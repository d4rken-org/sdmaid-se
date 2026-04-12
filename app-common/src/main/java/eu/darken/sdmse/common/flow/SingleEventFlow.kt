package eu.darken.sdmse.common.flow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.receiveAsFlow

class SingleEventFlow<T> : AbstractFlow<T>() {
    private val channel = Channel<T>(Channel.Factory.BUFFERED)

    override suspend fun collectSafely(collector: FlowCollector<T>) = channel.receiveAsFlow().collect(collector)

    suspend fun emit(value: T) = channel.send(value)

    fun tryEmit(value: T): ChannelResult<Unit> = channel.trySend(value)

    fun emitBlocking(value: T): ChannelResult<Unit> = channel.trySendBlocking(value)
}
