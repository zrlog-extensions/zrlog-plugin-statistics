#!/usr/bin/env bash
basePath=${1}
mkdir -p "${basePath}"
echo "real target folder ${basePath}"

java -version
mvnArgs=()
if [ $# -ge 2 ] && [ -n "${2}" ]; then
  mvnArgs=("${2}")
fi
./mvnw "${mvnArgs[@]}" clean
bash -e bin/build-info.sh "${basePath}"
./mvnw "${mvnArgs[@]}" -PnodeBuild package
./mvnw "${mvnArgs[@]}" -Pnative -Dagent exec:exec@java-agent -U
./mvnw "${mvnArgs[@]}" -Pnative package
binName="statistics"
targetFile=""
sourceFile=""
if [ -f "target/${binName}.exe" ];
then
  echo "window"
  sourceFile="target/${binName}.exe"
  targetFile="${basePath}/${binName}-Windows-$(uname -m).exe"
  mv ${sourceFile} ${targetFile}
  exit 0;
fi
if [[ "$(uname -s)" == "Linux" ]];
then
  echo "Linux"
  sourceFile="target/${binName}"
  targetFile="${basePath}/${binName}-$(uname -s)-$(dpkg --print-architecture).bin"
  mv ${sourceFile} ${targetFile}
else
  echo "MacOS"
  sourceFile="target/${binName}"
  targetFile="${basePath}/${binName}-$(uname -s)-$(uname -m).bin"
  mv ${sourceFile} ${targetFile}
fi
