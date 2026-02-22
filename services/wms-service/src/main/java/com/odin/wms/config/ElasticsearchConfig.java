package com.odin.wms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import java.net.URI;

/**
 * Configuração do cliente Elasticsearch.
 * Fix QA-1.1-001: usa URI.create() para parsing correto do host:port
 * ao invés de String.replace() frágil.
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Override
    public ClientConfiguration clientConfiguration() {
        URI uri = URI.create(elasticsearchUri);
        String hostAndPort = uri.getHost() + ":" + uri.getPort();
        return ClientConfiguration.builder()
            .connectedTo(hostAndPort)
            .build();
    }
}
