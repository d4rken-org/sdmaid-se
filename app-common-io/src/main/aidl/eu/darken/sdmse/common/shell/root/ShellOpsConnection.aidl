package eu.darken.sdmse.common.shell.root;

import eu.darken.sdmse.common.shell.root.ShellOpsCmd;
import eu.darken.sdmse.common.shell.root.ShellOpsResult;

interface ShellOpsConnection {

   ShellOpsResult execute(in ShellOpsCmd cmd);

}