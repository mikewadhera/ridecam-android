#!/bin/bash
# usage: ./poll-temp.sh <interval-sec>
#    ex: ./poll-temp.sh 5

>temp.log

while true; do
  
  adb shell cat /sys/class/thermal/thermal_zone10/temp >> temp.log
  
  echo "Sleeping..."
  
  sleep $1

done