package br.com.jrfotografia;

import br.com.jrfotografia.config.AppProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(AppProps.class)
@EnableJpaRepositories(considerNestedRepositories = true)
@org.springframework.scheduling.annotation.EnableScheduling
public class JrFotografiaApplication {

    public static void main(String[] args) {
        // Datas e horários da agenda no fuso do estúdio, mesmo em servidor configurado em UTC.
        TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("FUSO_ESTUDIO", "America/Fortaleza")));
        SpringApplication.run(JrFotografiaApplication.class, args);
    }
}
