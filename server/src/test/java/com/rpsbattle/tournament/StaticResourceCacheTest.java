package com.rpsbattle.tournament;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StaticResourceCacheTest extends StageThreeWebFixture {
    private HttpResponse<byte[]> read(String path) throws Exception {
        return accountHttp.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void generatedAdminModulesAndStylesUseOneImmutableNamespaceAndSupportCompression() throws Exception {
        var html=read("/admin/index.html");assertThat(html.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var body=new String(html.body(),java.nio.charset.StandardCharsets.UTF_8);
        var script=java.util.regex.Pattern.compile("/ui/([a-f0-9]{16})/js/admin/admin-shell[.]js").matcher(body);
        assertThat(script.find()).as("HTML must reference the build's versioned module").isTrue();String prefix="/ui/"+script.group(1);
        for(var path:java.util.List.of(prefix+"/js/admin/admin-shell.js",prefix+"/js/admin/dashboard.js",prefix+"/css/admin.css")){
            var asset=read(path);assertThat(asset.statusCode()).as(path).isEqualTo(200);
            assertThat(asset.headers().firstValue("Cache-Control").orElse("")).contains("max-age=31536000","public","immutable");
        }
        String css=new String(read(prefix+"/css/admin.css").body(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(css).contains(prefix+"/css/layout-spacing.css","/assets/castle-courtyard.620b83a9efcb.webp");
        var encoded=accountHttp.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+prefix+"/css/admin.css")).header("Accept-Encoding","gzip").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(encoded.headers().firstValue("Content-Encoding")).hasValue("gzip");
        try(var gzip=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(encoded.body()))){assertThat(new String(gzip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(css);}
        assertThat(read("/ui/not-a-build/js/admin/admin-shell.js").statusCode()).isEqualTo(404);
    }
    @Test void publicAssetsCacheAndRevalidateWithoutCachingPagesOrAccountResponses() throws Exception {
        var image=read("/assets/castle-courtyard.620b83a9efcb.webp");
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.body().length).isBetween(1,300_000);
        assertThat(image.headers().firstValue("Content-Type")).hasValue("image/webp");
        assertThat(image.headers().firstValue("Cache-Control").orElse("")).contains("max-age=86400","public").doesNotContain("no-store");
        assertThat(image.headers().firstValue("Pragma")).isEmpty();
        var modified=image.headers().firstValue("Last-Modified").orElseThrow();
        var cached=accountHttp.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/assets/castle-courtyard.620b83a9efcb.webp")).header("If-Modified-Since",modified).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(cached.statusCode()).isEqualTo(304);
        assertThat(cached.body()).isEmpty();
        for(var path:java.util.List.of("/css/battle-theme.css","/js/pages/home.js")) {
            var asset=read(path);assertThat(asset.statusCode()).isEqualTo(200);
            assertThat(asset.headers().firstValue("Cache-Control")).hasValue("no-cache");
        }
        for(var path:java.util.List.of("/","/api/auth/me","/assets/not-found.webp")) {
            assertThat(read(path).headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        }
    }
}
