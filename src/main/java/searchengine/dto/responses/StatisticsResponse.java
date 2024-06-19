package searchengine.dto.responses;

import lombok.Data;
import lombok.EqualsAndHashCode;
import searchengine.dto.statistics.StatisticsData;

@EqualsAndHashCode(callSuper = true)
@Data
public class StatisticsResponse extends Response{
    private boolean result;
    private StatisticsData statistics;
}
