package hcmute.edu.zentech.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AiAgentRuntimeResponse {
    private String content;
    private boolean fallback;
    private boolean handoffRecommended;
    private List<RetrievedContext> retrievedContext = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RetrievedContext {
        private String id;
        private String content;
        private double score;
        private String source;
        private String datasetId;
        private String documentId;
    }
}
