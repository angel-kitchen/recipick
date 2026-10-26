package com.recipick.global.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import com.recipick.IntegrationTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

// 관리 포트는 별도 서버라 MockMvc로 확인할 수 없어 실제 HTTP 요청을 보낸다.
@AutoConfigureObservability
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0"
)
class ActuatorExposureTest extends IntegrationTestSupport {

    @LocalServerPort
    private int appPort;

    @LocalManagementPort
    private int managementPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("관리 포트에서는 토큰 없이 헬스 체크가 200이다")
    void health_onManagementPort() throws Exception {
        // given
        String path = "/actuator/health";

        // when
        HttpResponse<String> response = get(managementPort, path);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("관리 포트에서는 토큰 없이 Prometheus 지표를 읽을 수 있다")
    void prometheus_onManagementPort() throws Exception {
        // given
        String path = "/actuator/prometheus";

        // when
        HttpResponse<String> response = get(managementPort, path);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("# HELP");
    }

    @Test
    @DisplayName("앱 포트에서는 헬스 체크와 지표가 모두 열리지 않는다")
    void actuator_onAppPort_denied() throws Exception {
        // given
        String healthPath = "/actuator/health";
        String prometheusPath = "/actuator/prometheus";

        // when
        HttpResponse<String> health = get(appPort, healthPath);
        HttpResponse<String> prometheus = get(appPort, prometheusPath);

        // then
        assertThat(health.statusCode()).isEqualTo(401);
        assertThat(prometheus.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("기존 공개 경로는 앱 포트에서 그대로 열려 있다")
    void publicApi_onAppPort_stillOpen() throws Exception {
        // given
        String path = "/api/categories";

        // when
        HttpResponse<String> response = get(appPort, path);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
