package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.responses.Response;
import searchengine.exceptions.InvalidInputException;
import searchengine.models.SiteModel;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinPool;

public interface IndexingService {

    Response indexAllSites();

    boolean isIndexingGoingOnNow();

    void startIndexing(Site site, ForkJoinPool forkJoinPool)
            throws InterruptedException, BrokenBarrierException;

    Response indexPage(String path);

    String getCorrectPathForm(String path) throws InvalidInputException;

    SiteModel findOrCreateSiteByPagePath(String path) throws InvalidInputException;

    Response stopIndexing();

    Response getResponse(String error);


    boolean getIndexingStatus();

    int getPagesCountOfSite(Site site);

    int getLemmasCountOfSite(Site site);

    String getSiteStatus(Site site);

    long getSiteStatusTime(Site site);

    String getSiteError(Site site);
}
