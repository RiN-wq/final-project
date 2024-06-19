package searchengine.dto.responses;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class SimpleSuccessfulResponse extends Response {
    private final boolean result = true;
}
