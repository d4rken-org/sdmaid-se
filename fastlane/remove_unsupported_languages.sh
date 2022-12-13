#!/bin/sh
echo "Removing unsupported languages..."
echo `pwd`
rm -rv ./metadata/android/es-AR
rm -rv ./metadata/android/sc-IT
rm -rv ./metadata/android/sq-AL
rm -rv ./metadata/android/uz
rm -rv ./metadata/android/pcm-NG
rm -rv ./metadata/android/tl-PH
rm -rv ./metadata/android/ku-TR
rm -rv ./metadata/android/kmr-TR
rm -rv ./metadata/android/ur-IN
rm -rv ./metadata/android/zu
rm -rv ./metadata/android/si-LK
find ./metadata/android -empty -type d -delete
exit 0