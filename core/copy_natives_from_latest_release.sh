#!/usr/bin/env bash
set -euo pipefail

yellow="\033[0;33m"
green="\033[0;32m"
red="\033[0;31m"
clear="\033[0m"

# Download voicechat-discord
file="src/main/resources/voicechat-discord.jar"
if [ ! -f $file ]; then
  url=$(curl -g -s "https://api.modrinth.com/v2/project/S1jG5YV5/version" | jq -r '.[0].files[0].url')

  echo -e -n "${yellow}Downloading voicechat-discord from ${clear}$url${yellow}..."
  curl -s -o $file $url
  echo -e "downloaded${clear}"
else
  echo -e "${green}voicechat-discord already downloaded${clear}"
fi

cd src/main/resources

echo -e "${yellow}Extracting natives${yellow}..."
jar xf voicechat-discord.jar natives

echo -e "${yellow}Removing old jar${yellow}..."
rm -v voicechat-discord.jar

echo -e "${green}Done!${clear}"
