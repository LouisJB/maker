DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export JAVA_OPTS="-Xmx1500m -javaagent:$DIR/../lib/jrebel.jar -noverify"
