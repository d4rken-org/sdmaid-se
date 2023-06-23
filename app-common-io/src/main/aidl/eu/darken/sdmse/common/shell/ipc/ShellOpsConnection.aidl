package eu.darken.sdmse.common.shell.ipc;

import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd;
import eu.darken.sdmse.common.shell.ipc.ShellOpsResult;

interface ShellOpsConnection {

   ShellOpsResult execute(in ShellOpsCmd cmd);

}