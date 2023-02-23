#!/bin/bash

while getopts d: flag
do
    case "${flag}" in
        d) device_id=${OPTARG};;
    esac
done

if [ -z "$device_id" ] ;then
    echo "Missing device ID, use '-d <DEVICE_ID>')"
    exit 1
else
    echo "Device ID is $device_id"
fi

connected_devices=$(adb devices | sed "1 d")

if [[ "$connected_devices" == *"$device_id"* ]] ;then
    echo "Device found"
else
    echo "Unknown device"
    echo "Connected devices are:"
    echo "$connected_devices"
    exit 1
fi

echo ""
start_path=$(pwd)
echo "Work path: $start_path"

echo "Setting up CorpseFinder data..."
cd $start_path
cd dirt-sources/corpsefinder/sdcard
zip -r corpsefinder-sdcard.zip *
adb -s $device_id push corpsefinder-sdcard.zip /sdcard/
adb -s $device_id shell cd /sdcard/ && unzip -o corpsefinder-sdcard.zip
adb -s $device_id shell rm /sdcard/corpsefinder-sdcard.zip
rm corpsefinder-sdcard.zip
echo "CorpseFinder is setup!"
echo ""

echo "Setting up SystemCleaner data"
cd $start_path
cd dirt-sources/systemcleaner/sdcard
zip -r systemcleaner-sdcard.zip *
adb -s $device_id push systemcleaner-sdcard.zip /sdcard/
adb -s $device_id shell cd /sdcard/ && unzip -o systemcleaner-sdcard.zip
adb -s $device_id shell rm /sdcard/systemcleaner-sdcard.zip
rm systemcleaner-sdcard.zip
echo "SystemCleaner is setup!"
echo ""

echo "Setting up AppCleaner data"
cd $start_path
cd dirt-sources/appcleaner/sdcard
zip -r appcleaner-sdcard.zip *
adb -s $device_id push appcleaner-sdcard.zip /sdcard/
adb -s $device_id shell cd /sdcard/ && unzip -o appcleaner-sdcard.zip
adb -s $device_id shell rm /sdcard/appcleaner-sdcard.zip
rm appcleaner-sdcard.zip
echo "AppCleaner is setup!"
echo ""