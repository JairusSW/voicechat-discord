#!/usr/bin/env fish

set platform $argv[1]
echo platform: $platform

cd $platform
set version_directories ./1.*
cd ..

for dir in $version_directories
    set ver $(basename $dir)
    # switch to version
    set_color yellow; echo switching to $ver; set_color normal
    sed -E -e '/"'$platform':/s/[^:]*",$/'$ver'",/' -i settings.gradle.kts
    if ! ./gradlew :$platform:$ver:build
        exit 1
    end
    echo
end
