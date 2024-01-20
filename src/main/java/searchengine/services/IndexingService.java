package searchengine.services;

import searchengine.config.Site;
import searchengine.model.SiteModel;
import searchengine.model.Status;

import java.util.List;

public interface IndexingService {
    public List<String> indexAllSites();
    public List<String> indexPage(Site site);
    public boolean startIndexing(List<Site> sitesList);
    public boolean startIndexing(Site site);
    public void clearSitesFromTable(List<Site> sites);
    public void clearSitesFromTable(Site site);
    public List<String> stopIndexing();
    public List<String> getResponse(boolean errorCheckerFlag, String error);
    public SiteModel createOrUpdateSite(String url, String name, Status status);
}
