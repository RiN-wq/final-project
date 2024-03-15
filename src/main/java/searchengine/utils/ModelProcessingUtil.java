package searchengine.utils;

import org.jsoup.Connection;
import org.jsoup.select.Elements;
import searchengine.config.Site;
import searchengine.exceptions.ClientException;
import searchengine.exceptions.DuplicateException;
import searchengine.exceptions.RedirectionException;
import searchengine.exceptions.ServerException;
import searchengine.models.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ModelProcessingUtil {
    SiteModel createOrUpdateSiteModel(Site site, Status status, SiteModel siteModel);

    SiteModel setStatusTimeToSiteModel(SiteModel siteModel);

    Map<PageModel, Elements> createOrUpdatePageModel(SiteModel siteModel,
                                                     String path,
                                                     PageModel pageModel) throws
            IOException, DuplicateException, RedirectionException, ClientException, ServerException;

    PageModel createOrUpdatePageModel(SiteModel siteModel,
                                      String path) throws
            IOException, DuplicateException, RedirectionException, ClientException, ServerException;

    void checkIfThePageIsTheMainPage(SiteModel siteModel,
                                     PageModel pageModel,
                                     Exception e);

    void throwExceptionByStatusCode(int statusCode) throws RedirectionException, ClientException, ServerException;

    void getClientException(int statusCode) throws ClientException;

    String getPageModelExceptionMessage(Exception e);

    PageModel setPathToPageModel(String path);

    Connection.Response getConnection(String path) throws IOException;

    LemmaModel createOrUpdateLemmaModel(SiteModel siteModel,
                                        String lemma,
                                        LemmaModel lemmaModel);

    LemmaModel synchronizedAddFrequency(LemmaModel lemmaModel, String lemma);

    void saveIndexModel(PageModel pageModel,
                        LemmaModel lemmaModel,
                        float rank);

    void clearTables(SiteModel siteModel);

    void clearTables(PageModel pageModel);

    void clearLemmaAndIndexTablesByPageModel(List<IndexModel> indexModelList);

    void deleteOrSaveInBatch(List<LemmaModel> lemmasForProcessing,
                             List<IndexModel> indexesForProcessing,
                             boolean deletingFlag);


}
