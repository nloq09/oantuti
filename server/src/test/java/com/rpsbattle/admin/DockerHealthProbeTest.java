package com.rpsbattle.admin;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DockerHealthProbeTest {
    @Test void unavailableRuntimeIsCachedAndDoesNotInventAContainerCount(){var probe=new DockerHealthProbe("definitely-missing-rps-docker-executable","local");var value=probe.status();assertThat(value.get("status")).isEqualTo("DOWN");assertThat(value.get("managedContainers")).isNull();assertThat(probe.status()).isSameAs(value);assertThat(value.toString()).doesNotContain("docker.sock","exception","stack");}
}
