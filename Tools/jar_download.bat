@echo off
setlocal enabledelayedexpansion
rem MavenリポジトリのベースURL
set BASE_URL=https://repo1.maven.org/maven2

rem Tools/libフォルダのパス
set LIB_DIR=%~dp0lib

mkdir %LIB_DIR%

for %%f in (
com/oracle/database/jdbc/ojdbc8/23.6.0.24.10/ojdbc8-23.6.0.24.10.jar 
commons-io/commons-io/2.11.0/commons-io-2.11.0.jar 
commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar
org/apache/commons/commons-collections4/4.4/commons-collections4-4.4.jar 
org/apache/commons/commons-compress/1.21/commons-compress-1.21.jar 
org/apache/logging/log4j/log4j-api/2.24.3/log4j-api-2.24.3.jar 
org/apache/logging/log4j/log4j-core/2.24.3/log4j-core-2.24.3.jar 
org/apache/poi/poi-ooxml-lite/5.2.3/poi-ooxml-lite-5.2.3.jar
org/apache/poi/poi-ooxml-schemas/4.1.2/poi-ooxml-schemas-4.1.2.jar 
org/apache/poi/poi-ooxml/5.2.3/poi-ooxml-5.2.3.jar 
org/apache/poi/poi/5.2.3/poi-5.2.3.jar 
org/apache/xmlbeans/xmlbeans/5.1.1/xmlbeans-5.1.1.jar 
org/codehaus/groovy/groovy-all/2.4.21/groovy-all-2.4.21.jar 
org/duckdb/duckdb_jdbc/1.1.3/duckdb_jdbc-1.1.3.jar 
org/postgresql/postgresql/42.7.5/postgresql-42.7.5.jar 
com/h2database/h2/2.3.232/h2-2.3.232.jar
) do (
    set "JAR_PATH=%%f"
    set "FILENAME=%%~nxf"
    bitsadmin /TRANSFER "myDownloadJob" /download /priority normal !BASE_URL!/!JAR_PATH! !LIB_DIR!\!FILENAME!
)

7z d lib\duckdb_jdbc-1.1.3.jar libduckdb_java.so_linux_amd64
7z d lib\duckdb_jdbc-1.1.3.jar libduckdb_java.so_linux_arm64
7z d lib\duckdb_jdbc-1.1.3.jar libduckdb_java.so_osx_universal

echo All jars have been downloaded.
pause

