package eu.darken.sdmse.common.files.core

import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.FlowCollector
import java.util.*

// TODO support symlinks?
// TODO unit test coverage
class PathTreeFlow<
        P : APath,
        PL : APathLookup<P>,
        GT : APathGateway<P, PL>
        > constructor(
    private val gateway: GT,
    private val start: P,
) : AbstractFlow<PL>() {

    override suspend fun collectSafely(collector: FlowCollector<PL>) {
        val startLookUp = start.lookup(gateway)
        if (startLookUp.isFile) {
            collector.emit(startLookUp)
            return
        }

        val queue = LinkedList(listOf(startLookUp))

        while (!queue.isEmpty()) {

            val lookUp = queue.removeFirst()

            lookUp.lookedUp.lookupFiles(gateway).forEach { child ->
                if (child.isDirectory) queue.addFirst(child)
                collector.emit(child)
            }
        }
    }


}