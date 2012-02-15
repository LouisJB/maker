#!/bin/bash

MAKER_LIB_DIR=.maker/lib

mkdir -p $MAKER_LIB_DIR
MAKER_BOOTSTRAP=true
#MAKER_IVY_UPDATE=true
JAVA_HOME=/usr/local/jdk/

main() {
  process_options $*
  if [ $MAKER_IVY_UPDATE ];
  then
    ivy_update
  fi
  if [ $MAKER_BOOTSTRAP ];
  then
    echo hi
    foo=12345
    variable_safely foo
    bootstrap
  fi
  if [ $MAKER_LAUNCH ];
  then
    export JAVA_OPTS="-Xmx$(($MAKER_HEAP_SPACE))m -Xms$(($MAKER_HEAP_SPACE / 10))m $JREBEL_OPTS"
    export CLASSPATH="$MAKER_CLASSPATH:$(external_jars)"
    echo $CLASSPATH
    #$(scala_home)/bin/scala -Yrepl-sync -nc -i $MAKER_PROJECT_FILE
  fi
}

external_jars() {
  echo `ls .maker/lib/*.jar | xargs | sed 's/ /:/g'`
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

  for module in utils plugin maker; do
    for src_dir in src tests; do
      SRC_FILES="$SRC_FILES $(find $module/$src_dir -name '*.scala' | xargs)"
    done
  done


  echo "$(scala_home)/bin/fsc -classpath $(external_jars) -d out $SRC_FILES"
  $(scala_home)/bin/fsc -classpath $(external_jars) -d out $SRC_FILES || Error "Failed to compile"
  echo "$(java_home)/bin/jar cf maker.jar out/"
  $(java_home)/bin/jar cf maker.jar out/

}

process_options() {
  set_default_options

  while true; do
    case "$1" in
      -h | --help ) display_usage; shift;;
      -p | --project-file ) MAKER_PROJECT_FILE=$2; shift 2;;
      -c | --maker-classpath ) MAKER_CLASSPATH=$2; shift 2;;
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
  -m, --mem-heap-space <heap space in MB>
  -s, --skip-ivy-update
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

ivy_settings(){
  if [ ! -z $MAKER_IVY_SETTINGS_FILE ];
  then
    echo " -settings $MAKER_IVY_SETTINGS_FILE "
  elif [ -e "ivysettings.xml" ]
  then
    echo " -settings ivysettings.xml "
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
  result="$(ivy_command) -types jar -sync"
  echo $result
  echo `$result`
  result="$(ivy_command) -types bundle"
  echo $result
  echo `$result`
  echo "done"
}

set_default_options() {
  MAKER_PROJECT_FILE="Maker.scala"
  JREBEL_OPTS=""
  MAKER_IVY_FILE="ivy.xml"

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
