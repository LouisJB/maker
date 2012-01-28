#!/bin/bash

os=${OSTYPE//[0-9.]/}
echo "OS type = $os"

if [ ! -f $SCALA_HOME/bin/fsc ];
then
  echo "Can't find fsc, set SCALA_HOME"
  exit -1
fi

# belt and braces initial state to prevent any funny stuff!
rm -rf out
export CLASSPATH=

# Set java heap size to something nice and big
if [ -z $MAKER_BUILD_MEM ];
then
  if [ os==darwin ];
  then
    totalMem=$(sysctl hw.memsize | awk '/[:s]/ {print $2}')
    totalMem=$[$totalMem/1024]
  else
    totalMem=$(cat /proc/meminfo | head -n 1 | awk '/[0-9]/ {print $2}')
  fi
    echo "Total memory = $totalMem MB"
    mem=$[$totalMem/1024/4]
else
  mem=$MAKER_BUILD_MEM
fi

if [ $mem -lt 1024 ];
then
  echo "Memory may be too low - try setting MAKER_BUILD_MEM to at least 2000"
fi

echo "Using ${mem} MB of memory"
export JAVA_OPTS="-Xmx${mem}m"

# build maker once
$SCALA_HOME/bin/fsc -shutdown
./bin/build-from-scratch.rb

# use maker to build itself
./bin/write-classpath.rb
. ./set-maker-classpath.sh
$SCALA_HOME/bin/fsc -reset

$SCALA_HOME/bin/scala -nc -Yrepl-sync -i Maker.scala -e "{ val m =  makerProj; m compile}"

./bin/write-classpath.rb

echo "Maker built successfully"
echo "Note, use '. set-maker-classpath.sh' to set up the classpath for maker in the environment of this shell"

