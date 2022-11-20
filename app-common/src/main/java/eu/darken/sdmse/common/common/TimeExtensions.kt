package eu.darken.sdmse.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

fun OffsetDateTime.toSystemTimezone(): ZonedDateTime = this
    .atZoneSameInstant(ZoneId.systemDefault())

fun Instant.toSystemTimezone(): ZonedDateTime = this
    .atZone(ZoneId.systemDefault())