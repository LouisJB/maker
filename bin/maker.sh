#!/bin/bash

# This script is designed to be run from the root of a project. 
# i.e.
# $ cd <project-root-dir>
# $ <maker-root-dir>/bin/maker.sh
# 
# This project may or may not be maker itself. To avoid further confusion, the
# following convention is used to distinguish maker and project variables.
#
# MAKER_OWN_...         refer to maker itself
# MAKER_PROJECT_...     refer to the project
#
# The script does the following
# 1. Download via ivy the jars required by maker itself 
# 2. Build maker.jar
# 3. Set classpath and heap space
# 4. Launch the repl, loading the project
# 
# Steps 1 and 2 are omitted if they have been done earlier - unless overridden in
# in the options.
# 

MAKER_OWN_ROOT_DIR="$( cd "$(dirname $( dirname "${BASH_SOURCE[0]}" ))" && pwd )"
MAKER_PROJECT_ROOT_DIR=`pwd`

set -e

MAKER_OWN_LIB_DIR=$MAKER_OWN_ROOT_DIR/.maker/lib
MAKER_PROJECT_SCALA_LIB_DIR=.maker/scala-lib

mkdir -p .maker

main() {
  process_options $*
  check_setup_sane || exit -1

  if [ $MAKER_IVY_UPDATE ] || [ ! -e $MAKER_OWN_LIB_DIR ];
  then
    ivy_update
  else
    echo "Omitting ivy update as $MAKER_OWN_LIB_DIR exists"
  fi
  
  if [ $MAKER_BOOTSTRAP ] || [ ! -e $MAKER_OWN_ROOT_DIR/maker.jar ];
  then
    bootstrap || exit -1
  else
    echo "Omitting bootstrap as $MAKER_OWN_ROOT_DIR/maker.jar exists"
  fi

  if [ $MAKER_DOWNLOAD_PROJECT_LIB ] || [ ! -e $MAKER_PROJECT_SCALA_LIB_DIR ];
  then
    download_scala_library_and_compiler
  fi


  if [ -z $MAKER_SKIP_LAUNCH ];
  then
    export JAVA_OPTS="-Xmx$(($MAKER_HEAP_SPACE))m -Xms$(($MAKER_HEAP_SPACE / 10))m $JREBEL_OPTS"
    export CLASSPATH="$MAKER_OWN_ROOT_DIR/maker.jar:$(external_jars):resources/"
    export JAVA_OPTS="$JAVA_OPTS $MAKER_DEBUG_PARAMETERS "
    echo $CLASSPATH
    $SCALA_HOME/bin/scala -Yrepl-sync -nc -i $MAKER_PROJECT_FILE 
  fi
}

check_setup_sane(){
  if [ -z $SCALA_HOME ];
  then
    echo "SCALA_HOME not defined"
    exit -1
  fi

  if [ -z $JAVA_HOME ];
  then
    echo "JAVA_HOME not defined"
    exit -1
  fi

  MAKER_IVY_JAR=${MAKER_IVY_JAR-/usr/share/java/ivy.jar} 
  if [ ! -e $MAKER_IVY_JAR ];
  then
    echo "Ivy jar not found"
    exit -1
  fi

  if [ -z $MAKER_PROJECT_FILE ];
  then
    declare -a arr
    i=0
    for file in `ls *.scala`; do
      arr[$i]=$file
      ((i++))
    done
    if [ ${#arr[@]} != 1 ];
    then
      echo "Either specify project file or have a single Scala file in the top level"
      exit -1
    fi
    MAKER_PROJECT_FILE="${arr[0]}"
  fi


  MAKER_HEAP_SPACE=${MAKER_HEAP_SPACE-$(calc_heap_space)}
}

calc_heap_space(){
  os=${OSTYPE//[0-9.]/}
  if [ "$os" = "darwin" ];
  then
    totalMem=$(sysctl hw.memsize | awk '/[:s]/ {print $2}')
    totalMem=$(($totalMem/1024))
  else
    totalMem=$(cat /proc/meminfo | head -n 1 | awk '/[0-9]/ {print $2}')
  fi
  echo "$(($totalMem/1024/4))"
}

run_command(){
  command=$1
  $command || (echo "failed to run $command " && exit -1)
}

external_jars() {
  cp=`ls $MAKER_OWN_ROOT_DIR/.maker/lib/*.jar | xargs | sed 's/ /:/g'`
  cp=$cp:`ls $MAKER_OWN_ROOT_DIR/libs/*.jar | xargs | sed 's/ /:/g'`
  echo $cp
}

bootstrap() {

  pushd $MAKER_OWN_ROOT_DIR  # Shouldn't be necessary to change dir, but get weird compilation errors otherwise
  MAKER_OWN_CLASS_OUTPUT_DIR=$MAKER_OWN_ROOT_DIR/out
  MAKER_OWN_JAR=$MAKER_OWN_ROOT_DIR/maker.jar

  rm -rf $MAKER_OWN_CLASS_OUTPUT_DIR
  mkdir $MAKER_OWN_CLASS_OUTPUT_DIR
  rm -f $MAKER_OWN_JAR
  for module in utils plugin maker; do
    for src_dir in src tests; do
      SRC_FILES="$SRC_FILES $(find $MAKER_OWN_ROOT_DIR/$module/$src_dir -name '*.scala' | xargs)"
    done
  done

  echo "Compiling"
  run_command "$SCALA_HOME/bin/fsc -classpath $(external_jars) -d $MAKER_OWN_CLASS_OUTPUT_DIR $SRC_FILES" || exit -1
  echo "Building jar"
  run_command "$JAVA_HOME/bin/jar cf $MAKER_OWN_JAR -C $MAKER_OWN_CLASS_OUTPUT_DIR ." || exit -1
  if [ ! -e $MAKER_OWN_ROOT_DIR/maker.jar ];
  then
	  echo "Maker jar failed to be created"
	  exit -1
  fi

  popd

}

process_options() {

  while true; do
    case "${1-""}" in
      -h | --help ) display_usage; exit 0;;
      -p | --project-file ) MAKER_PROJECT_FILE=$2; shift 2;;
      -j | --use-jrebel ) set_jrebel_options; shift;;
      -m | --mem-heap-space ) MAKER_HEAP_SPACE=$2; shift 2;;
      -y | --do-ivy-update ) MAKER_IVY_UPDATE=true; shift;;
      -b | --boostrap ) MAKER_BOOTSTRAP=true; shift;;
      -d | --download-project-scala-lib ) $MAKER_DOWNLOAD_PROJECT_LIB=true; shift;;
      -x | --allow-remote-debugging ) MAKER_DEBUG_PARAMETERS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"; shift;;
      --ivy-proxy-host ) MAKER_IVY_PROXY_HOST=$2; shift 2;;
      --ivy-proxy-port ) MAKER_IVY_PROXY_PORT=$2; shift 2;;
      --ivy-non-proxy-hosts ) MAKER_IVY_NON_PROXY_HOSTS=$2; shift 2;; 
      --ivy-jar ) MAKER_IVY_JAR=$2; shift 2;; 
      -- ) shift; break;;
      *  ) break;;
    esac
  done

  REMAINING_ARGS=$*
}

display_usage() {
cat << EOF

  usage
    maker.sh <option>*

  options
    -h, --help
    -p, --project-file <project-file>
    -j, --use-jrebel (requires JREBEL_HOME to be set)
    -m, --mem-heap-space <heap space in MB> 
      default is one quarter of available RAM
    -y, --do-ivy-update 
      update will always be done if <maker-dir>/.maker/lib doesn't exist
    -b, --boostrap 
      builds maker.jar from scratch
    -d, --download-project-scala-lib 
      downloads scala compiler and library to <project-dir>/.maker/scala-lib
      download is automatic if this directory does not exist
    -x, --allow-remote-debugging
      runs a remote JVM
    --ivy-proxy-host <host>
    --ivy-proxy-port <port>
    --ivy-non-proxy-hosts <host,host,...>
    --ivy-jar <file>        
      defaults to /usr/share/java/ivy.jar

EOF
}


ivy_command(){
  ivy_file=$1
  lib_dir=$2
  if [ ! -e $lib_dir ];
  then
    mkdir -p $lib_dir
  fi
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
  command="$command -jar $MAKER_IVY_JAR -ivy $ivy_file"
  command="$command -settings $MAKER_OWN_ROOT_DIR/ivysettings.xml "
  command="$command -retrieve $lib_dir/[artifact]-[revision](-[classifier]).[ext] "
  echo $command
}


ivy_update() {
  echo "Updating ivy"
  MAKER_IVY_FILE="$MAKER_OWN_ROOT_DIR/ivy.xml"
  run_command "$(ivy_command $MAKER_IVY_FILE $MAKER_OWN_LIB_DIR) -types jar -sync"
  run_command "$(ivy_command $MAKER_IVY_FILE $MAKER_OWN_LIB_DIR) -types bundle"
  run_command "$(ivy_command $MAKER_IVY_FILE $MAKER_OWN_LIB_DIR) -types source "
}

download_scala_library_and_compiler(){
  ivy_file=.maker/scala-lib-ivy.xml
  rm -f $ivy_file
  if [ ! -e $ivy_file ];
  then
cat > $ivy_file << EOF
<ivy-module version="1.0" xmlns:e="http://ant.apache.org/ivy/extra">
  <info organisation="maker" module="maker"/>
  <configurations>
    <conf name="default" transitive="false"/>
  </configurations>
  <dependencies defaultconfmapping="*->default,sources">
    <dependency org="org.scala-lang" name="scala-compiler" rev="2.9.1"/>
    <dependency org="org.scala-lang" name="scala-library" rev="2.9.1"/>
  </dependencies>
</ivy-module>
EOF
  command="$(ivy_command $ivy_file $MAKER_PROJECT_SCALA_LIB_DIR ) -types jar -sync"
  run_command "$command"
  command="$(ivy_command $ivy_file $MAKER_PROJECT_SCALA_LIB_DIR ) -types source"
  run_command "$command"
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
