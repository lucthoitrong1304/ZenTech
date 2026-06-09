package hcmute.edu.zentech.repository.projection;

public interface TransactionStatsProjection {
    Long getTotalImports();
    Long getTotalExports();
    Long getTotalCount();
}
