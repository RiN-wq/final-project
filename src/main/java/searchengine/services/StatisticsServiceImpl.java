package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;

import java.util.ArrayList;
import java.util.List;

@Service
//@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final IndexingService indexingService;

    public  StatisticsServiceImpl(SitesList sites, IndexingService indexingService){
        this.sites = sites;
        this.indexingService = indexingService;
    }

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();

        total.setSites(sites.getSites().size());
        total.setIndexing(indexingService.getIndexingStatus());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();

        for (Site site : sitesList) {
            addStatisticsSite(site, total, detailed);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    public void addStatisticsSite(Site site,
                                  TotalStatistics total,
                                  List<DetailedStatisticsItem> detailed){

        DetailedStatisticsItem item = new DetailedStatisticsItem();

        item.setName(site.getName());
        item.setUrl(site.getUrl());

        int pages = indexingService.getPagesCountOfSite(site);
        int lemmas = indexingService.getLemmasCountOfSite(site);

        item.setPages(pages);
        item.setLemmas(lemmas);

        item.setStatus(indexingService.getSiteStatus(site));
        item.setError(indexingService.getSiteError(site));
        item.setStatusTime(indexingService.getSiteStatusTime(site));

        total.setPages(total.getPages() + pages);
        total.setLemmas(total.getLemmas() + lemmas);
        detailed.add(item);
    }
}
