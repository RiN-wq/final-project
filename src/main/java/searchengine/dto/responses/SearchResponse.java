package searchengine.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import searchengine.dto.searching.SearchData;

import java.util.List;

@Data
@Component
public class SearchResponse {

    private boolean result;
    private int count;
    private List<SearchData> data;

}
