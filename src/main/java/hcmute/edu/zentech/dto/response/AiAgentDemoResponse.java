package hcmute.edu.zentech.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AiAgentDemoResponse {
    private String content;
    private boolean fallback;
    private boolean handoffRecommended;
    private List<RetrievedContext> retrievedContext;

    @Getter
    @Builder
    public static class RetrievedContext {
        private String id;
        private String content;
        private double score;
        private String source;
        private String datasetId;
        private String documentId;
    }
}
