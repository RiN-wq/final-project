package searchengine.services;

import searchengine.config.Site;
import searchengine.model.SiteModel;
import searchengine.model.Status;

import java.util.List;

public interface IndexingService {
    public List<String> indexAllSites();
    public List<String> indexPage(String path);
    public String checkSiteByPage(String path);
    public boolean startIndexing(List<Site> sitesList);
    public void clearSiteAndPageTables(List<Site> sites);
    public List<String> stopIndexing();
    public List<String> getResponse(boolean errorCheckerFlag, String error);
    public SiteModel createOrUpdateSite(String url, String name, Status status);
    boolean getIndexingStatus();
    int getPagesCountOfSite(Site site);
    int getLemmasCountOfSite(Site site);
    String getSiteStatus(Site site);
    long getSiteStatusTime(Site site);
    public String getSiteError(Site site);
}
