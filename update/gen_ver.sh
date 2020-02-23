#!/bin/sh
cp ../build/vsalem.jar .
cp ../build/lclient-res.jar .
for i in $(find . -type f -exec echo {} \; | cut -c3- |grep -v ver); do echo "$i=$(sha256sum $i|cut -d' ' -f 1)"; done > ver.tmp
sha256sum ver.tmp|cut -d' ' -f 1 >ver
cat ver.tmp >>ver
echo "builtin-res.jar=$(sha256sum ../build/builtin-res.jar |cut -d' ' -f 1)" >>ver
echo "salem-res.jar=$(sha256sum ../build/salem-res.jar |cut -d' ' -f 1)" >>ver

