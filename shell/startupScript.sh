#!/bin/sh
curl "http://code.mathieu-bolla.com/maven/snapshot/aws-sdk-bootstraper/aws-sdk-bootstraper/1.0-SNAPSHOT/aws-sdk-bootstraper-1.0-20131018.100941-1-jar-with-dependencies.jar" > bootstraper.jar
java -cp bootstraper.jar com.mathieu_bolla.bootstraper.Bootstraper code.myproject.com aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar ./aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar
java -cp aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar com.myproject.Launcher run
shutdown -h now