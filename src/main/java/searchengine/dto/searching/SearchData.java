package searchengine.dto.searching;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class SearchData {

    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

}
