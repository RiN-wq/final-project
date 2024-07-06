package searchengine.utils;

import org.jsoup.Connection;
import org.jsoup.select.Elements;
import searchengine.config.Site;
import searchengine.exceptions.ClientException;
import searchengine.exceptions.DuplicateException;
import searchengine.exceptions.WebException;
import searchengine.models.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public interface ModelProcessingUtil {
    void initializeSetOfPaths(List<Site> sites);
    boolean isPathChecked(String path);
    boolean writeCheckedPath(String path);
    SiteModel createOrUpdateSiteModel(Site site, Status status, SiteModel siteModel);

    SiteModel setStatusTimeToSiteModel(SiteModel siteModel);

    Map<PageModel, Elements> createOrUpdatePageModel(SiteModel siteModel,
                                                     String path,
                                                     PageModel pageModel) throws
            IOException, DuplicateException, WebException;

    PageModel createOrUpdatePageModel(SiteModel siteModel,
                                      String path) throws
            IOException, WebException;

    void checkIfThePageIsTheMainPage(SiteModel siteModel,
                                     String path,
                                     Exception e);

    void throwExceptionByStatusCode(int statusCode) throws WebException;

    void getClientException(int statusCode) throws ClientException;

    String getPageModelExceptionMessage(Exception e);

    PageModel setPathToPageModel(String path);

    Connection.Response getConnection(String path) throws IOException;

    LemmaModel createOrUpdateLemmaModel(SiteModel siteModel,
                                        String lemma,
                                        LemmaModel lemmaModel);

    void saveIndexModel(PageModel pageModel,
                        LemmaModel lemmaModel,
                        float rank);

    void clearTables(SiteModel siteModel, CountDownLatch countDownLatchOfClear);
    void deleteIndexesInBatch(List<PageModel> pageModelList, List<IndexModel> batchForDeleting);

    void clearTables(PageModel pageModel);

    void clearLemmaAndIndexTablesByPageModel(List<IndexModel> indexModelList);

    void deleteOrSaveInBatch(List<LemmaModel> lemmasForProcessing,
                             List<IndexModel> indexesForProcessing,
                             boolean deletingFlag);


}
