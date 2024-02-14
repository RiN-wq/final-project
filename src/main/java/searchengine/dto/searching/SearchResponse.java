package searchengine.dto.searching;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class SearchResponse {
    private String url;
    private String title;
    private String snippet;
    private float relevance;
}
