package searchengine.services;

import searchengine.config.Site;
import searchengine.dto.responses.SimpleResponse;
import searchengine.exceptions.InvalidInputException;
import searchengine.models.*;

import java.util.List;

public interface IndexingService {
    List<SimpleResponse> indexAllSites();

    boolean isIndexingGoingOnNow();

    void startIndexing(List<Site> sitesList, List<SimpleResponse> response) throws InterruptedException;

    List<SimpleResponse> indexPage(String path);

    String getCorrectPathForm(String path) throws InvalidInputException;

    SiteModel findOrCreateSiteByPagePath(String path) throws InvalidInputException;

    List<SimpleResponse> stopIndexing();

    List<SimpleResponse> getResponse(boolean errorCheckerFlag,
                                     String error);

    boolean getIndexingStatus();

    int getPagesCountOfSite(Site site);

    int getLemmasCountOfSite(Site site);

    String getSiteStatus(Site site);

    long getSiteStatusTime(Site site);

    String getSiteError(Site site);
}
