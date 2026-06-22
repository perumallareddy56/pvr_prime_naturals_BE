@echo off
set "JAVA_HOME=C:\Users\DELL\.jdks\ms-17.0.18"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Starting PVR Prime Naturals Backend (CLEAN BUILD) with JAVA_HOME=%JAVA_HOME%
"C:\Users\DELL\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd" clean spring-boot:run
