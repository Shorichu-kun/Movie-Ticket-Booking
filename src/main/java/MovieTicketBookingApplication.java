import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@ComponentScan(basePackages = {"handler", "model", "repository", "service", "utils"})
public class MovieTicketBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieTicketBookingApplication.class, args);
    }
}