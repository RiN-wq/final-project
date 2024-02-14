package searchengine.dto.indexing;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class IndexingStop {
    private volatile boolean stopIndexingFlag;
}
