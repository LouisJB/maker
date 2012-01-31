DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
mem=$(cat /proc/meminfo | head -n 1 | awk '/[0-9]/ {print $2}')

export JAVA_OPTS="-Xmx$[$mem/1020/4]m -Xms$[$mem/1020/4/10]m -javaagent:$DIR/../lib/jrebel.jar -noverify"
