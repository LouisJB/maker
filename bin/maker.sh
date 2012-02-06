#!/bin/sh

main() {
  process_options
  export JAVA_OPTS="-Xmx$(($MAKER_HEAP_SPACE))m -Xms$(($MAKER_HEAP_SPACE / 10))m $JREBEL_OPTS"
  echo $JAVA_OPTS
  echo $MAKER_PROJECT_FILE
  #$SCALA_HOME/bin/scala -nc -i $MAKER_PROJECT_FILE
}

process_options() {
  set_default_options

  while true; do
    case "$1" in
      -h | --help ) display_usage; shift;;
      -p | --project-file ) MAKER_PROJECT_FILE=$2; shift 2;;
      -j | --use-jrebel ) set_jrebel_options; shift;;
      -m | --mem-heap-space ) MAKER_HEAP_SPACE=$2; shift 2;;
      -- ) shift; break;;
      *  ) break;;
    esac
  done
}

display_usage() {
cat << EOF
  -h, --help
  -p, --project-file <project-file>
  -j, --use-jrebel (requires JREBEL_HOME to be set)
  -m, --mem-heap-space <heap space in MB>
EOF
}

set_default_options() {
  MAKER_PROJECT_FILE="Maker.scala"
  JREBEL_OPTS=""

  # Set java heap size to something nice and big
  if [ -z $MAKER_HEAP_SPACE ];
  then
    if [ "$os" = "darwin" ];
    then
      totalMem=$(sysctl hw.memsize | awk '/[:s]/ {print $2}')
      totalMem=$(($totalMem/1024))
    else
      totalMem=$(cat /proc/meminfo | head -n 1 | awk '/[0-9]/ {print $2}')
    fi
    MAKER_HEAP_SPACE=$(($totalMem/1024/4))
  fi
}


set_jrebel_options() {
  if [ ! -f $JREBEL_HOME/jrebel.jar ];
  then
    echo "Can't find jrebel.jar, set JREBEL_HOME"
    exit 1
  fi
  JREBEL_OPTS=" -javaagent:$JREBEL_HOME/jrebel.jar -noverify"
}

main;
