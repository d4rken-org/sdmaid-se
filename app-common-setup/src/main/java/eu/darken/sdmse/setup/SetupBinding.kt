package eu.darken.sdmse.setup

import javax.inject.Qualifier

@Qualifier
@MustBeDocumented
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class SetupBinding(val type: SetupModule.Type)
