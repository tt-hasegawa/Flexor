@echo off
set JAVA_HOME=%~dp0jre
set PATH=%JAVA_HOME%\bin;%PATH%;
set classpath=
setlocal enabledelayedexpansion
for %%a in (%~dp0lib\*.jar) do ( set classpath=!CLASSPATH!;%%a)
java -cp !CLASSPATH! -Dfile.encoding=UTF-8 -Xmx1536m groovy.ui.GroovyMain %~dp0src\DataFileMergeTool.groovy %1
pause
