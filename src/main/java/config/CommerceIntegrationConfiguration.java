package config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import service.commerce.CommerceService;
import utils.CommerceInventory;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@DependsOn("fileStorageConfiguration")
public class CommerceIntegrationConfiguration {
  public CommerceIntegrationConfiguration(
      CommerceService commerce, @Value("${cinema.data-dir:data}") String directory) {
    CommerceInventory.register(
        Path.of(directory),
        commerce::isCommerceSeatOccupied,
        commerce::hasActiveShowtime,
        commerce::hasActiveSeat);
    new repository.TicketRepository().migrateReservations();
  }
}
