package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.responses.StatisticsResponse;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.TotalStatistics;

import java.util.List;

public interface StatisticsService {
    StatisticsResponse getStatistics();

    void addStatisticsSite(Site site,
                           TotalStatistics total,
                           List<DetailedStatisticsItem> detailed);
}
