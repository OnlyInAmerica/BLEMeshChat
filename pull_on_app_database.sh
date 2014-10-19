#!/bin/bash

# Pull the database for this application to the Desktop

adb backup -f ~/Desktop/ble.ab -noapk pro.dbro.ble
dd if=~/Desktop/ble.ab bs=1 skip=24 | python -c "import zlib,sys;sys.stdout.write(zlib.decompress(sys.stdin.read()))" | tar -xvf -