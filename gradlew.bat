@ECHO OFF
where gradle >NUL 2>&1
if %ERRORLEVEL% neq 0 (
  ECHO ERROR: 'gradle' command not found in PATH. Please install Gradle 8.6+ or restore gradle-wrapper.jar.
  EXIT /B 1
)

gradle %*
