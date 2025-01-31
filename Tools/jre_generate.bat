winget install --id=ojdkbuild.openjdk.17.jdk -e

set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1
set PATH=%JAVA_HOME%\bin;%PATH%
echo JAVA_HOME is set to %JAVA_HOME%

set JLINK_OUTPUT=%~dp0\jre
set MODULES=java.base,java.logging,java.sql,java.sql.rowset,java.datatransfer,java.desktop,java.management,java.management.rmi,java.prefs,java.rmi,java.scripting,java.xml,jdk.unsupported

jlink.exe --compress=2 --module-path "%JAVA_HOME%\jmods" --add-modules %MODULES% --output "%JLINK_OUTPUT%"

echo Custom JRE created at %JLINK_OUTPUT%
