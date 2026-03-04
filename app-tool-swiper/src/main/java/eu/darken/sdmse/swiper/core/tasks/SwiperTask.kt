package eu.darken.sdmse.swiper.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface SwiperTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.SWIPER

    sealed interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.SWIPER
    }
}
