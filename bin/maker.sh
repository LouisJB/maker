#!/bin/bash

fsc -reset

./bin/build-from-scratch.rb
./bin/write-classpath.rb

. ./set-maker-classpath.sh

. ./bin/set-opts.sh

fsc -reset

$SCALA_HOME/bin/scala -Yrepl-sync -i Maker.scala -e "maker compile"

. ./set-classpath.sh

