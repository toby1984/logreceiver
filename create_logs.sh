#!/bin/bash

X="0"
while [ "$X" -lt "1000" ] ; do 
  logger message${X}
  X=`expr ${X} + 1`
done
