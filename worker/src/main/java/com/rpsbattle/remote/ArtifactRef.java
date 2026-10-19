package com.rpsbattle.remote;
import java.util.*;
public record ArtifactRef(UUID artifactId,String artifactHash,String language,String runtime) {
    public ArtifactRef {
        if(artifactId==null||artifactHash==null||!artifactHash.matches("[a-f0-9]{64}")||language==null||!Set.of("PYTHON","CPP","JAVA").contains(language)||!Map.of("PYTHON","python:3.12-slim","CPP","gcc:14","JAVA","eclipse-temurin:21-jdk").get(language).equals(runtime))throw new IllegalArgumentException("Invalid artifact descriptor");
    }
    public String format(){return switch(language){case "PYTHON"->"PYTHON_SOURCE";case "CPP"->"CPP_BINARY";case "JAVA"->"JAVA_JAR";default->throw new IllegalStateException();};}
}
