package com.rpsbattle.tournament;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StaticResourceCacheTest extends StageThreeWebFixture {
    private HttpResponse<byte[]> read(String path) throws Exception {
        return accountHttp.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
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
