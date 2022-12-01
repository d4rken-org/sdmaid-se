package eu.darken.sdmse.common.pkgs.container

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.ReadableApk

sealed class BasePkg : Pkg, ReadableApk, Installed