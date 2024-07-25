#!/bin/bash

set -e

# Function to get device ID using a selection menu
get_device_id() {
    devices=$(adb devices | sed "1 d" | awk '{print $1}')
    if [ -z "$devices" ]; then
        echo "No devices found. Please connect a device and try again."
        exit 1
    fi

    PS3="Select a device by number: "
    select selected_device in $devices; do
        if [ -n "$selected_device" ]; then
            echo "$selected_device"
            break
        else
            echo "Invalid selection. Please try again."
        fi
    done
}

device_id=$(get_device_id)

if [ -z "$device_id" ]; then
    echo "No device selected. Exiting."
    exit 1
else
    echo "Device ID is $device_id"
fi

connected_devices=$(adb devices | sed "1 d")

if [[ "$connected_devices" != *"$device_id"* ]]; then
    echo "Unknown device"
    echo "Connected devices are:"
    echo "$connected_devices"
    exit 1
fi

echo "Device found"
start_path=$(pwd)
echo "Work path: $start_path"

# Add ignored directories here
ignored_directories=("self" "emulated")

# Function to set up data in a given path
setup_data_in_path() {
    local dir=$1
    local full_path=$2
    local zip_file=$3

    echo "Trying $full_path..."
    if adb -s $device_id shell "[ -d $full_path ]"; then
        adb -s $device_id push "$zip_file" "$full_path/" || { echo "Failed to push $zip_file to $full_path"; return; }

        unzip_command="cd $full_path && unzip -o $zip_file && rm $zip_file"

        # Attempt unzip as normal user
        if ! adb -s $device_id shell "$unzip_command"; then
            echo "Retrying unzip with root privileges"
            adb -s $device_id shell "su root sh -c '$unzip_command'" || { echo "Failed to unzip with root for $full_path"; return; }
        fi

        echo "$dir setup completed at $full_path!"
        echo ""
    fi
}

# Function to handle the setup for a directory
setup_data() {
    local dir=$1
    local zip_file=${dir##*/}-sdcard.zip
    cd "$start_path/$dir/sdcard"
    zip -r "$zip_file" * || { echo "Failed to create zip file for $dir"; return; }

    for storage_path in $(adb -s $device_id shell ls /storage/ | tr -d '\r'); do
        if [[ " ${ignored_directories[@]} " =~ " ${storage_path} " ]]; then
            echo "Ignoring $storage_path"
            continue
        fi

        full_path="/storage/$storage_path"
        if [[ "$storage_path" == "emulated" ]]; then
            subdirectories=$(adb -s $device_id shell ls /storage/emulated/ | tr -d '\r')
            for subdirectory in $subdirectories; do
                setup_data_in_path "$dir" "$full_path/$subdirectory" "$zip_file"
            done
        else
            setup_data_in_path "$dir" "$full_path" "$zip_file"
        fi
    done
    rm "$zip_file" || echo "Failed to remove local zip file $zip_file"
}

setup_data "dirt-sources/corpsefinder"
setup_data "dirt-sources/systemcleaner"
setup_data "dirt-sources/appcleaner"
setup_data "dirt-sources/deduplicator"
