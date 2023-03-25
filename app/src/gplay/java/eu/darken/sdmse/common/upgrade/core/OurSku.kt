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
            override val offers: Collection<Sku.Subscription.Offer> = setOf(
                BASE_OFFER, TRIAL_OFFER
            )

            object BASE_OFFER : Sku.Subscription.Offer {
                override val basePlanId: String = "upgrade-pro-baseplan"
                override val offerId: String? = null
            }

            object TRIAL_OFFER : Sku.Subscription.Offer {
                override val basePlanId: String = "upgrade-pro-baseplan"
                override val offerId: String = "upgrade-pro-baseplan-trial"
            }
        }
    }

    companion object {
        val PRO_SKUS = setOf(Sub.PRO_UPGRADE, Iap.PRO_UPGRADE)
    }
}