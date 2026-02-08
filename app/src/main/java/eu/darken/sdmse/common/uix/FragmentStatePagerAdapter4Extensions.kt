package eu.darken.sdmse.common.uix

fun <T> FragmentStatePagerAdapter4<T>.setDataIfChanged(
    newData: List<T>,
    identifier: (T) -> Any,
): Boolean {
    if (data.map(identifier) != newData.map(identifier)) {
        setData(newData)
        notifyDataSetChanged()
        return true
    }
    return false
}
