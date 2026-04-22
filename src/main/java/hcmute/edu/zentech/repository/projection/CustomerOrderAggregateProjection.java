package hcmute.edu.zentech.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface CustomerOrderAggregateProjection {
    UUID getCustomerId();
    long getTotalOrders();
    double getTotalSpent();
    Instant getLastOrderAt();
}
