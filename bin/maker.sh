#!/bin/bash

# belt and braces initial state to prevent any funny stuff!
rm -rf out
export CLASSPATH=
fsc -shutdown

./bin/build-from-scratch.rb
./bin/write-classpath.rb

. ./set-maker-classpath.sh
. ./bin/set-opts.sh

fsc -reset

#$SCALA_HOME/bin/scala -nc -Yrepl-sync -i Maker.scala -e "maker compile"
$SCALA_HOME/bin/scala -nc -Yrepl-sync -i Maker.scala

. ./set-classpath.sh

