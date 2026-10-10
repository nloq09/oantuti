package com.rpsbattle.bot.upload;

import com.rpsbattle.online.DemoRequestException;
import java.util.Locale;

public enum BotLanguage {
    PYTHON("main.py", "python:3.12-slim", "python -I -u /bot/main.py"),
    CPP("main.cpp", "gcc:14", "g++ -std=c++17 -O2 /bot/main.cpp -o /tmp/bot && exec /tmp/bot"),
    JAVA("Main.java", "eclipse-temurin:21-jdk", "javac -J-Xmx128m -J-XX:ActiveProcessorCount=1 -d /tmp /bot/Main.java && exec java -Xmx128m -XX:ActiveProcessorCount=1 -cp /tmp Main"),
    JAR("bot.jar", "eclipse-temurin:21-jdk", "exec java -Xmx128m -XX:ActiveProcessorCount=1 -jar /bot/bot.jar");
    public final String filename, image, command;
    BotLanguage(String filename,String image,String command){this.filename=filename;this.image=image;this.command=command;}
    public static BotLanguage fromFilename(String filename){
        if(filename==null)throw new DemoRequestException("UNSUPPORTED_BOT_LANGUAGE");
        String name=filename.toLowerCase(Locale.ROOT);
        if(name.endsWith(".py"))return PYTHON;
        if(name.endsWith(".cpp"))return CPP;
        if(name.endsWith(".java"))return JAVA;
        if(name.endsWith(".jar"))return JAR;
        throw new DemoRequestException("UNSUPPORTED_BOT_LANGUAGE");
    }
}
