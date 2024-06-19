package searchengine.dto.responses;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;
import searchengine.dto.searching.SearchData;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class SearchResponse extends Response{

    private boolean result;
    private long count;
    private List<SearchData> data;

}
