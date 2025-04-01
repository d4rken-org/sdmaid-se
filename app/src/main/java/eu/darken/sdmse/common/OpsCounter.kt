package eu.darken.sdmse.common

class OpsCounter(
    private val tickRate: Long = 1000,
    private val logCall: (Float, Long) -> Unit,
) {
    private val start = System.currentTimeMillis()
    private var ops = 0
    private var lastLog = start

    fun tick() {
        ops++
        val now = System.currentTimeMillis()
        val elapsed = now - lastLog
        if (elapsed >= tickRate) {
            val opsRate = ops * tickRate.toFloat() / (now - start)
            lastLog = now
            logCall(opsRate, elapsed)
        }
    }
}