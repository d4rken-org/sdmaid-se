#!/bin/sh
export CROWDIN_PERSONAL_TOKEN=`cat ~/.config/projects/eu.darken.sdmse/crowdin.key`
alias crowdin-cli='java -jar crowdin-cli.jar'
crowdin-cli "$@"
