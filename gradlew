#!/bin/sh
set -eu

find_supported_java_home() {
  for dir in "$@"; do
    [ -n "$dir" ] || continue
    [ -x "$dir/bin/java" ] || continue
    version_output=$("$dir/bin/java" -version 2>&1 | sed -n '1p')
    major=$(printf '%s\n' "$version_output" | sed -n 's/.*version "\([0-9][0-9]*\)\([.].*\)\?".*/\1/p')
    case "$major" in
      17|18|19|20|21|22|23)
        printf '%s\n' "$dir"
        return 0
        ;;
    esac
  done
  return 1
}

current_java_home=${JAVA_HOME:-}
selected_java_home=$(find_supported_java_home \
  "$current_java_home" \
  /root/.local/share/mise/installs/java/* \
  /usr/lib/jvm/* \
  /Library/Java/JavaVirtualMachines/*/Contents/Home \
  ) || selected_java_home=''

if [ -n "$selected_java_home" ] && [ "$selected_java_home" != "$current_java_home" ]; then
  export JAVA_HOME="$selected_java_home"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "Using JAVA_HOME=$JAVA_HOME" >&2
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "ERROR: 'gradle' command not found in PATH. Please install Gradle 8.6+ or restore gradle-wrapper.jar." >&2
exit 1
