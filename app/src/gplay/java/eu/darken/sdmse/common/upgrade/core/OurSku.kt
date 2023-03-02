package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.upgrade.core.billing.Sku

@Suppress("ClassName")
interface OurSku {
    interface Iap : OurSku {
        object PRO_UPGRADE : Sku.Iap, Iap {
            override val id: String = "eu.darken.sdmse.iap.upgrade.pro"
        }
    }

    interface Sub : Sku.Subscription {
        object PRO_UPGRADE : Sku.Subscription, Sub {
            override val id: String = "upgrade.pro"
            override val plans: Collection<Sku.Subscription.Plan> = setOf(
                BASE_PLAN, TRIAL_PLAN
            )

            object BASE_PLAN : Sku.Subscription.Plan {
                override val planId: String = "upgrade-pro-baseplan"
            }

            object TRIAL_PLAN : Sku.Subscription.Plan {
                override val planId: String = "upgrade-pro-baseplan-trial"
            }
        }
    }

    companion object {
        val PRO_SKUS = setOf(Sub.PRO_UPGRADE, Iap.PRO_UPGRADE)
    }
}