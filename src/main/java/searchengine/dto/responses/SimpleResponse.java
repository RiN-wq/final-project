package searchengine.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class SimpleResponse {
    private boolean result;
    private String error;
}
