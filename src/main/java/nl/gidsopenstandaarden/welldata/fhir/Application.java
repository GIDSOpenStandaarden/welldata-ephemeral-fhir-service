package nl.gidsopenstandaarden.welldata.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import nl.gidsopenstandaarden.welldata.fhir.config.WellDataRestfulServer;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ServletRegistrationBean<WellDataRestfulServer> fhirServletRegistration(WellDataRestfulServer server) {
        ServletRegistrationBean<WellDataRestfulServer> registration = new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("FhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
