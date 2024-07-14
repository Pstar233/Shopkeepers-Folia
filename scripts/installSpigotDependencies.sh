#!/usr/bin/env bash

pushd "$(dirname "$BASH_SOURCE")"

# $1: Spigot MC version to build
# $2: "remapped" to check for a remapped server jar
# $3: Optional: The specific Spigot build number to build. If unset, we use $1 for this.
buildSpigotIfMissing() {
  local buildVersion="$1"
  local versionString="$1"
  local classifier=""
  local jarPath=""
  local installedImplementationVersion=""
  local installedBuildNumber=""
  local build="yes"

  if [ -n "$3" ]; then
    buildVersion="$3"
    versionString="$1 ($3)"
  fi
  if [ "$2" = "remapped" ]; then classifier="-remapped-mojang"; fi

  jarPath=$"$HOME/.m2/repository/org/bukkit/craftbukkit/$1-R0.1-SNAPSHOT/craftbukkit-$1-R0.1-SNAPSHOT${classifier}.jar"
  if [ -f "${jarPath}" ]; then
    installedImplementationVersion=$(unzip -p "${jarPath}" 'META-INF/MANIFEST.MF' | grep -oP '(?<=^Implementation-Version: ).*')
    installedBuildNumber=$(echo "${installedImplementationVersion}" | grep -oP '^\d+(?=-)')
    echo "Maven repository: Found Spigot $1 (${installedImplementationVersion}) (#${installedBuildNumber})"

    if [ -n "$3" ]; then
      if [ "${installedBuildNumber}" = "$3" ]; then
        build="no"
      fi
    else
      build="no"
    fi
  fi

  if [ "${build}" = "yes" ]; then
    ./installSpigot.sh "${buildVersion}"
  else
    echo "Not building Spigot ${versionString} because it is already in our Maven repository"
  fi
}

# We only re-build CraftBukkit/Spigot versions that are missing in the Maven cache.
# Add entries here for every required version of CraftBukkit/Spigot.

# The following versions require JDK 8 to build:
source installJDK.sh 8

buildSpigotIfMissing 1.16.5

# Optional argument 'minimal': Only builds the api and main projects, which only depend on the
# lowest supported Spigot version. So we can skip the building of later Spigot versions.
# TODO: Removed again. For some reason Jitpack does not find our build artifacts if we build any
# Spigot dependencies. So 'minimal' now skips the building of Spigot dependencies completely and
# only builds the api project, which can depend on externally available libraries.
#if [ $# -eq 1 ] && [ "$1" = "minimal" ]; then
#    echo "Minimal build: Skipping build of additional Spigot dependencies."
#    popd
#    exit 0
#fi

# The following versions require JDK 16 to build:
source installJDK.sh 16

buildSpigotIfMissing 1.17.1 remapped

# The following versions require JDK 17 to build:
source installJDK.sh 17

buildSpigotIfMissing 1.18.2 remapped
buildSpigotIfMissing 1.19.4 remapped
buildSpigotIfMissing 1.20.1 remapped
buildSpigotIfMissing 1.20.2 remapped
buildSpigotIfMissing 1.20.4 remapped

# The following versions require JDK 21 to build:
source installJDK.sh 21

buildSpigotIfMissing 1.20.6 remapped
# A change in Spigot 1.21 (2024-07-06) broke the plugin, so we require a version from before that.
buildSpigotIfMissing 1.21 remapped 4255

popd
