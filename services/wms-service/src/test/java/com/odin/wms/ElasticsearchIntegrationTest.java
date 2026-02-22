package com.odin.wms;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Elasticsearch Connection — Integration Tests")
class ElasticsearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    @DisplayName("Elasticsearch client bean is available")
    void elasticsearchClientBeanIsAvailable() {
        assertThat(elasticsearchClient).isNotNull();
    }

    @Test
    @DisplayName("Elasticsearch cluster is reachable")
    void elasticsearchClusterIsReachable() {
        assertThatCode(() -> {
            var info = elasticsearchClient.info();
            assertThat(info.clusterName()).isNotBlank();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Can check index existence without error")
    void canCheckIndexExistenceWithoutError() throws IOException {
        boolean exists = elasticsearchClient.indices()
            .exists(ExistsRequest.of(e -> e.index("wms-test-index")))
            .value();
        // Index likely doesn't exist yet — that's fine, we just test connectivity
        assertThat(exists).isFalse();
    }
}
