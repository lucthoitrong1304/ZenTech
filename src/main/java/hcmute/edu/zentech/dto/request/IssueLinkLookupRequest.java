package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IssueLinkLookupRequest {
    @NotEmpty
    private List<String> signatures;
}
