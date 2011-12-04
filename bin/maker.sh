#!/bin/bash

./bin/build-from-scratch.rb


./bin/write-classpath.rb

. ./set-maker-classpath.sh
export JAVA_OPTS="-Xmx2000m -javaagent:lib/jrebel.jar"

scala -Yrepl-sync -i Maker.scala

. ./set-classpath.sh

