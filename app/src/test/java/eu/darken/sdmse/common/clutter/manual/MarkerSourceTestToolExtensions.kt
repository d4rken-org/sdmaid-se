package eu.darken.sdmse.common.clutter.manual

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.Marker

suspend fun MarkerSourceTestTool.check(
    matchType: MarkerSourceTestTool.Candi.MatchType,
    areaType: DataArea.Type,
    flags: Collection<Marker.Flag>,
    packages: List<String>,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = matchType,
        areaType = areaType,
        flags = flags,
        packages = packages,
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.check(
    matchType: MarkerSourceTestTool.Candi.MatchType,
    areaType: DataArea.Type,
    flags: Collection<Marker.Flag> = emptySet(),
    packageName: String,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = matchType,
        areaType = areaType,
        flags = flags,
        packages = listOf(packageName),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.pos(
    areaType: DataArea.Type,
    flags: Collection<Marker.Flag> = emptySet(),
    packages: Collection<String>,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.POS,
        areaType = areaType,
        flags = flags,
        packages = packages,
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.pos(
    areaType: DataArea.Type,
    flag: Marker.Flag,
    packageName: String,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.POS,
        areaType = areaType,
        flags = setOf(flag),
        packages = listOf(packageName),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.pos(
    areaType: DataArea.Type,
    flags: Collection<Marker.Flag> = emptySet(),
    packageName: String,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.POS,
        areaType = areaType,
        flags = flags,
        packages = listOf(packageName),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.pos(
    areaType: DataArea.Type,
    packageName: String,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.POS,
        areaType = areaType,
        flags = emptySet(),
        packages = listOf(packageName),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.neg(
    areaType: DataArea.Type,
    flags: Collection<Marker.Flag> = emptySet(),
    packages: Collection<String>,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.NEG,
        areaType = areaType,
        flags = flags,
        packages = packages,
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}


suspend fun MarkerSourceTestTool.neg(
    areaType: DataArea.Type,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.NEG,
        areaType = areaType,
        flags = emptySet(),
        packages = emptySet(),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.neg(
    areaType: DataArea.Type,
    flags: Collection<Marker.Flag> = emptySet(),
    packageName: String,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.NEG,
        areaType = areaType,
        flags = flags,
        packages = listOf(packageName),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}

suspend fun MarkerSourceTestTool.neg(
    areaType: DataArea.Type,
    packageName: String,
    prefixFreePath: String,
) {
    val candi = MarkerSourceTestTool.Candi(
        matchType = MarkerSourceTestTool.Candi.MatchType.NEG,
        areaType = areaType,
        flags = emptySet(),
        packages = listOf(packageName),
        prefixFreePath = prefixFreePath,
    )

    checkCandidates(candi)
}