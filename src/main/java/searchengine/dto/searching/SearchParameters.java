package searchengine.dto.searching;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class SearchParameters {
    private String query;
    private String site;
    private int offset = 0;
    private int limit = 20;

}
