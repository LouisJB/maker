#!/bin/bash

MAKER_DIR="$( cd "$(dirname $( dirname "${BASH_SOURCE[0]}" ))" && pwd )"
SAVED_DIR=`pwd`
echo $MAKER_DIR

set -e

MAKER_LIB_DIR=$MAKER_DIR/.maker/lib

main() {
  process_options $*
  if [ $MAKER_IVY_UPDATE ] || [ ! -e $MAKER_LIB_DIR ];
  then
    ivy_update
  fi
  if [ $MAKER_BOOTSTRAP ] || [ ! -e $MAKER_DIR/maker.jar ];
  then
    bootstrap
  fi
  if [ -z $MAKER_SKIP_LAUNCH ];
  then
    export JAVA_OPTS="-Xmx$(($MAKER_HEAP_SPACE))m -Xms$(($MAKER_HEAP_SPACE / 10))m $JREBEL_OPTS"
    export CLASSPATH="$MAKER_DIR/maker.jar:$(external_jars)"
    #$(scala_home)/bin/scala -Yrepl-sync -nc -i $MAKER_PROJECT_FILE
  fi
}

run_command(){
  command=$1
  $command || (echo "failed to run $command " && exit -1)
}

external_jars() {
  echo `ls $MAKER_DIR/.maker/lib/*.jar | xargs | sed 's/ /:/g'`
}

scala_home(){
  if [ -z $SCALA_HOME ];
  then
    echo "SCALA_HOME not defined"
    exit -1
  else
    echo $SCALA_HOME
  fi
}

java_home(){
  if [ -z $JAVA_HOME ];
  then
    echo "JAVA_HOME not defined"
    exit -1
  else
    echo $JAVA_HOME
  fi
}

bootstrap() {

  pushd $MAKER_DIR  # Shouldn't be necessary to change dir, but get weird compilation errors otherwise
  rm -rf out
  mkdir out
  for module in utils plugin maker; do
    for src_dir in src tests; do
      SRC_FILES="$SRC_FILES $(find $MAKER_DIR/$module/$src_dir -name '*.scala' | xargs)"
    done
  done

  echo "Compiling"
  run_command "$(scala_home)/bin/fsc -classpath $(external_jars) -d out $SRC_FILES"
  echo "Building jar"
  run_command "$(java_home)/bin/jar cf maker.jar -C out/ ."
  popd

}

process_options() {
  set_default_options

  while true; do
    case "$1" in
      -h | --help ) display_usage; shift;;
      -p | --project-file ) MAKER_PROJECT_FILE=$2; shift 2;;
      -j | --use-jrebel ) set_jrebel_options; shift;;
      -m | --mem-heap-space ) MAKER_HEAP_SPACE=$2; shift 2;;
      -y | --do-ivy-update ) MAKER_IVY_UPDATE=true; shift;;
      -b | --boostrap ) MAKER_BOOTSTRAP=true; shift;;
      --ivy-proxy-host ) MAKER_IVY_PROXY_HOST=$2; shift 2;;
      --ivy-proxy-port ) MAKER_IVY_PROXY_PORT=$2; shift 2;;
      --ivy-non-proxy-hosts ) MAKER_IVY_NON_PROXY_HOSTS=$2; shift 2;; 
      --ivy-jar ) MAKER_IVY_JAR=$2; shift 2;; 
      --ivy-file ) MAKER_IVY_FILE=$2; shift 2;; 
      --ivy-settings ) MAKER_IVY_SETTINGS_FILE=$2; shift 2;; 
      -- ) shift; break;;
      *  ) break;;
    esac
  done

  REMAINING_ARGS=$*
}

display_usage() {
cat << EOF
  -h, --help
  -p, --project-file <project-file>
  -j, --use-jrebel (requires JREBEL_HOME to be set)
  -m, --mem-heap-space <heap space in MB> (default is one quarter of available RAM)
  -y, --do-ivy-update (update will always be done if .maker/lib doesn't exist)
  -b, --boostrap (build maker.jar from scratch)
  --ivy-proxy-host 
  --ivy-proxy-port 
  --ivy-non-proxy-hosts 
  --ivy-jar (defaults to /usr/share/java/ivy.jar)
  --ivy-file (defaults to <maker-dir>/ivy.xml)
  --ivy-settings  (defaults to <maker-dir>/ivysettings.xml)
EOF
}

ivy_jar(){
  if [ ! -z $MAKER_IVY_JAR ];
  then
    echo $MAKER_IVY_JAR
  elif [ -e /usr/share/java/ivy.jar ];
  then
    echo "/usr/share/java/ivy.jar"
  else
    echo "Ivy jar not found"
    exit -1
  fi
}

error(){
  echo $1
  cd $SAVED_DIR
  exit -1
}

ivy_settings(){
  if [ ! -z $MAKER_IVY_SETTINGS_FILE ];
  then
    echo " -settings $MAKER_IVY_SETTINGS_FILE "
  elif [ -e "$MAKER_DIR/ivysettings.xml" ]
  then
    echo " -settings $MAKER_DIR/ivysettings.xml "
  fi
}

ivy_command(){
  command="java "
  if [ ! -z $MAKER_IVY_PROXY_HOST ];
  then
    command="$command -Dhttp.proxyHost=$MAKER_IVY_PROXY_HOST"
  fi
  if [ ! -z $MAKER_IVY_PROXY_PORT ];
  then
    command="$command -Dhttp.proxyPort=$MAKER_IVY_PROXY_PORT"
  fi
  if [ ! -z $MAKER_IVY_NON_PROXY_HOSTS ];
  then
    command="$command -Dhttp.nonProxyHosts=$MAKER_IVY_NON_PROXY_HOSTS"
  fi
  command="$command -jar $(ivy_jar) -ivy $MAKER_IVY_FILE "
  command="$command $(ivy_settings) "
  command="$command -retrieve $MAKER_LIB_DIR/[artifact]-[revision](-[classifier]).[ext] "
  echo $command
}


ivy_update() {
  echo "Updating ivy"
  mkdir -p $MAKER_LIB_DIR
  result="$(ivy_command) -types jar -sync"
  run_command "$result"
  result="$(ivy_command) -types bundle"
  run_command "$result"
  result="$(ivy_command) -types source "
  run_command "$result"
}

set_default_options() {
  MAKER_PROJECT_FILE="$MAKER_DIR/Maker.scala"
  JREBEL_OPTS=""
  MAKER_IVY_FILE="$MAKER_DIR/ivy.xml"

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

main $*
