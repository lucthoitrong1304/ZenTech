package hcmute.edu.zentech.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class MarkdownContentRequest {
    @Valid
    private List<MarkdownSectionRequest> sections;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MarkdownSectionRequest {
        @Size(max = 255, message = "heading must not exceed 255 characters")
        private String heading;

        private List<@Size(max = 5000, message = "paragraph must not exceed 5000 characters") String> paragraphs;

        @Valid
        private List<MarkdownBulletRequest> bullets;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MarkdownBulletRequest {
        @Size(max = 255, message = "label must not exceed 255 characters")
        private String label;

        @Size(max = 5000, message = "value must not exceed 5000 characters")
        private String value;
    }
}
