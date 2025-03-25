package eu.darken.sdmse.common.sieve

import eu.darken.sdmse.common.files.Segments

interface FileSieve {

    fun Collection<CriteriaOperator>.match(target: Segments): Boolean = any { op -> op.match(target) }

    fun Collection<NameCriterium>.matchAny(target: String): Boolean = any { crit -> crit.match(target) }

    fun Collection<SegmentCriterium>.matchAny(target: Segments): Boolean = any { crit -> crit.match(target) }

}