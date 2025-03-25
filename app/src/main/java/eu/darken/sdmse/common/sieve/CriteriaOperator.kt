package eu.darken.sdmse.common.sieve

import eu.darken.sdmse.common.files.Segments

sealed interface CriteriaOperator : Criterium {

    fun match(target: Segments): Boolean

    data class And(
        val criteria: List<SieveCriterium>,
    ) : CriteriaOperator {
        constructor(vararg crits: SieveCriterium) : this(crits.asList())

        override fun match(target: Segments): Boolean = criteria.all { crit ->
            when (crit) {
                is NameCriterium -> crit.match(target.last())
                is SegmentCriterium -> crit.match(target)
            }
        }

    }

    data class Or(
        val criteria: List<SieveCriterium>,
    ) : CriteriaOperator {
        constructor(vararg crits: SieveCriterium) : this(crits.asList())

        override fun match(target: Segments): Boolean = criteria.any { crit ->
            when (crit) {
                is NameCriterium -> crit.match(target.last())
                is SegmentCriterium -> crit.match(target)
            }
        }
    }
}

fun SegmentCriterium.wrap() = CriteriaOperator.And(listOf(this))