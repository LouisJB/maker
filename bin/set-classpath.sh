#!/bin/bash


CLASSPATH=`ls .maker/lib/*.jar | xargs | sed 's/ /:/g'`
CLASSPATH=$CLASSPATH:`ls libs/*.jar | xargs | sed 's/ /:/g'`
for module in utils plugin maker; do
  CLASSPATH=$CLASSPATH:$module/classes/:$module/test-classes/
done
CLASSPATH=$CLASSPATH:resources/
export CLASSPATH
