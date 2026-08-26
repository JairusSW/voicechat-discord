#!/usr/bin/env bash
set -euo pipefail

pluginVersion="3.2.0-geneva.32"
fabricLoaderVersion="0.19.3"

minecraftVersion="$2"
platform="$1"

yellow="\033[0;33m"
green="\033[0;32m"
red="\033[0;31m"
clear="\033[0m"

if [ "$minecraftVersion" == "" ]; then
  echo -e "${red}Please specify a minecraft version${clear}"
  exit 1
fi

if [ "$platform" == "paper" ]; then
  runDir="paper/run/$minecraftVersion"

  echo -e "${yellow}Setting up Paper server on version $minecraftVersion at $runDir${clear}"

  # Make server directories
  mkdir -p "$runDir/plugins/voicechat-discord"

  # Server (based on https://docs.papermc.io/misc/downloads-api/#downloading-the-latest-stable-build)
  file="$runDir/server.jar"
  if [ ! -f $file ]; then
    echo -e "${yellow}Getting server jar URL...${clear}"
    url=$(curl -s -H "User-Agent: voicechat-discord/0.0.0 (https://gitlab.com/amsam0/voicechat-discord)" "https://fill.papermc.io/v3/projects/paper/versions/$minecraftVersion/builds" | jq -r '.[0].downloads."server:default".url')

    echo -n -e "${yellow}Downloading server jar from ${clear}$url${yellow} to ${clear}$file${yellow}..."
    curl -s -o $file $url
    echo -e "downloaded${clear}"
  else
    echo -e "${green}Server jar already downloaded${clear}"
  fi

  voicechatFile="$runDir/plugins/voicechat-bukkit.jar"

  configFile="$runDir/plugins/voicechat-discord/config.yml"

  # Copy plugin
  from="paper/build/libs/voicechat-discord-paper-$pluginVersion.jar"
  to="$runDir/plugins/voicechat-discord-paper.jar"
  cp $from $to
  echo -e "${green}Copied plugin from $from to $to${clear}"

elif [ "$platform" == "fabric" ]; then
  runDir="fabric/$minecraftVersion/run"

  echo -e "${yellow}Setting up Fabric server on version $minecraftVersion at $runDir${clear}"

  mkdir -p "$runDir/config"
  mkdir -p "$runDir/mods"

  voicechatFile="$runDir/mods/voicechat-fabric.jar"

  configFile="$runDir/config/voicechat-discord.yml"

elif [ "$platform" == "neoforge" ]; then
  runDir="neoforge/$minecraftVersion/runs/server"

  echo -e "${yellow}Setting up NeoForge server on version $minecraftVersion at $runDir${clear}"

  mkdir -p "$runDir/config"
  mkdir -p "$runDir/mods"

  voicechatFile="$runDir/mods/voicechat-neoforge.jar"

  configFile="$runDir/config/voicechat-discord.yml"

else
  echo -e "${red}Unknown platform $platform${clear}"
  exit 1
fi

# Download voicechat
if [ ! -f $voicechatFile ]; then
  echo -e "${yellow}Getting voicechat URL...${clear}"
  voicechatUrl=$(curl -g -s "https://api.modrinth.com/v2/project/9eGKb6K1/version?game_versions=[%22$minecraftVersion%22]&loaders=[%22$platform%22]" | jq -r '.[0].files[0].url')

  echo -e -n "${yellow}Downloading voicechat from ${clear}$voicechatUrl${yellow} to ${clear}$voicechatFile${yellow}..."
  curl -s -o $voicechatFile $voicechatUrl
  echo -e "downloaded${clear}"
else
  echo -e "${green}voicechat already downloaded${clear}"
fi

# Copy eula.txt
eula="paper/run/*/eula.txt"
eula=$(echo $eula | cut -d " " -f 1 -)
if [ -f $eula ]; then
  to="$runDir/eula.txt"
  cp $eula $to && echo -e "${green}Copied eula.txt from $eula to $to${clear}"
else
  echo -e "${yellow}No eula.txt could be found${clear}"
fi

# Copy server.properties
serverProperties="paper/run/*/server.properties"
serverProperties=$(echo $serverProperties | cut -d " " -f 1 -)
if [ -f $serverProperties ]; then
  to="$runDir/server.properties"
  cp $serverProperties $to && echo -e "${green}Copied server.properties from $serverProperties to $to${clear}"
else
  echo -e "${yellow}No server.properties could be found${clear}"
fi

# Copy ops.json
opsJson="paper/run/*/ops.json"
opsJson=$(echo $opsJson | cut -d " " -f 1 -)
if [ -f $opsJson ]; then
  to="$runDir/ops.json"
  cp $opsJson $to && echo -e "${green}Copied ops.json from $opsJson to $to${clear}"
else
  echo -e "${yellow}No ops.json could be found${clear}"
fi

# Copy config
from="config.yml"
cp $from $configFile
echo -e "${green}Copied config from $from to $configFile${clear}"

#cd "$platform/run/$minecraftVersion"

# Remove session.lock
#for from in world*/session.lock; do
#  to="${from}_"
#  echo -e "${yellow}Moving $from to $to${clear}"
#  mv $from $to
#done

#echo -e "${green}Running version $minecraftVersion on platform $platform${clear}"
#java -Xmx1G -jar server.jar --nogui
