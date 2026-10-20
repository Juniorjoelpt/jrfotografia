package br.com.jrfotografia;

import br.com.jrfotografia.config.AppProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableConfigurationProperties(AppProps.class)
@EnableJpaRepositories(considerNestedRepositories = true)
public class JrFotografiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(JrFotografiaApplication.class, args);
    }
}
