package com.rpsbattle.bot.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpsbattle.online.DemoRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BotUploadServiceTest {
    @TempDir Path root;
    BotUploadService service(){return new BotUploadService(new ObjectMapper(),root.toString(),false);}
    MockMultipartFile file(String filename,byte[] data){return new MockMultipartFile("file",filename,"application/octet-stream",data);}
    void rejects(String code,Runnable action){assertEquals(code,assertThrows(DemoRequestException.class,action::run).code());}
    @Test void languageAndFixedStorageNamesIgnoreClientPaths()throws Exception{
        for(var language:BotLanguage.values()){
            byte[] data=language==BotLanguage.JAR?new byte[]{'P','K',3,4}:"source".getBytes();
            var uploaded=service().upload(" My bot ",file("../../evil/"+language.filename,data));
            assertEquals(language,uploaded.language());assertEquals("My bot",uploaded.name());
            assertArrayEquals(data,Files.readAllBytes(root.resolve(uploaded.id()).resolve(language.filename)));
            assertEquals(uploaded,service().authorize(uploaded.id(),uploaded.token()));
        }
        assertFalse(Files.exists(root.resolve("evil")));
    }
    @Test void invalidTokenCannotUseAnotherBotAndMissingRunnerFailsClosed(){
        var uploaded=service().upload("bot",file("bot.py","print(1)".getBytes()));
        rejects("BOT_ACCESS_DENIED",()->service().agent(uploaded.id(),"wrong"));
        rejects("BOT_NOT_FOUND",()->service().authorize("../../metadata.json",uploaded.token()));
        rejects("BOT_SANDBOX_UNAVAILABLE",()->service().agent(uploaded.id(),uploaded.token()));
        assertEquals(false,((java.util.Map<?,?>)service().capabilities().get("languages")).get("PYTHON"));
    }
    @Test void validatesNamesExtensionsEmptyOversizedAndBinarySources(){
        rejects("INVALID_BOT_NAME",()->service().upload(" ",file("bot.py",new byte[]{1})));
        rejects("UNSUPPORTED_BOT_LANGUAGE",()->service().upload("bot",file("bot.exe",new byte[]{1})));
        rejects("BOT_FILE_SIZE",()->service().upload("bot",file("bot.cpp",new byte[0])));
        rejects("BOT_FILE_SIZE",()->service().upload("bot",file("bot.py",new byte[512*1024+1])));
        rejects("BOT_FILE_SIZE",()->service().upload("bot",file("bot.jar",new byte[5*1024*1024+1])));
        rejects("INVALID_BOT_FILE",()->service().upload("bot",file("bot.java",new byte[]{0})));
        rejects("INVALID_BOT_FILE",()->service().upload("bot",file("bot.jar","not a jar".getBytes())));
    }
    @Test void storageQuotaRejectsFurtherUploads()throws Exception{
        for(int i=0;i<100;i++)Files.createDirectory(root.resolve("existing-"+i));
        rejects("BOT_STORAGE_FULL",()->service().upload("bot",file("bot.py",new byte[]{1})));
    }
    @Test void dockerCommandRestrictsEverySupportedLanguage(){
        for(var language:BotLanguage.values()){
            var command=DockerBotProcess.command(root.resolve(language.filename),language,"rps-bot-test");
            assertEquals("docker",command.getFirst());
            assertTrue(command.containsAll(java.util.List.of("--network=none","--read-only","--cap-drop=ALL",
                    "--security-opt=no-new-privileges:true","--user=65534:65534","--pids-limit=64",
                    "--memory=384m","--cpus=0.5","--pull=never",language.image)));
            assertEquals(language.command,command.getLast());
            assertEquals(1,command.stream().filter("--mount"::equals).count());
            assertTrue(command.get(command.indexOf("--mount")+1).endsWith(",target=/bot/"+language.filename+",readonly"));
            assertFalse(command.contains("--privileged"));
        }
    }
}
