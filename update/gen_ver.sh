#!/bin/sh

# Grab build results
cp ../build/vsalem.jar .
cp ../build/lclient-res.jar .

# Calculate all checksums
for i in $(find . -type f -exec echo {} \; | cut -c3- |grep -ve ver\$|grep -v gen_ver|grep -v ver.tmp); do echo "$i=$(sha256sum $i|cut -d' ' -f 1)"; done > ver.tmp

# Include Salem redistributables
echo "builtin-res.jar=$(sha256sum ../build/builtin-res.jar |cut -d' ' -f 1)" >>ver.tmp
echo "salem-res.jar=$(sha256sum ../build/salem-res.jar |cut -d' ' -f 1)" >>ver.tmp

# Finally calculate checksum of checksums
sha256sum ver.tmp|cut -d' ' -f 1 >ver
cat ver.tmp >>ver
rm ver.tmp

