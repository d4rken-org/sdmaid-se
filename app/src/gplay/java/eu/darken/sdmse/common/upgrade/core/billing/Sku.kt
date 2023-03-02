package eu.darken.sdmse.common.upgrade.core.billing

interface Sku {
    val id: String
    val type: Type

    interface Iap : Sku {
        override val id: String
        override val type: Type
            get() = Type.IAP
    }

    interface Subscription : Sku {
        override val id: String
        override val type: Type
            get() = Type.SUBSCRIPTION

        val plans: Collection<Plan>

        interface Plan {
            val planId: String
        }
    }

    enum class Type {
        IAP,
        SUBSCRIPTION,
        ;
    }

}