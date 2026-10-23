import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"handler", "model", "repository", "service", "utils"})
public class MovieTicketBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieTicketBookingApplication.class, args);
    }
}