#!/bin/sh
for i in $(find . -type f -exec echo {} \; | cut -c3- |grep -v ver); do echo "$i=$(sha256sum $i|cut -d' ' -f 1)"; done > ver.tmp
sha256sum ver.tmp|cut -d' ' -f 1 >ver
cat ver.tmp >>ver

