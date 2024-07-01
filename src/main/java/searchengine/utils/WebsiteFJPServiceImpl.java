package searchengine.utils;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.dto.indexing.IndexingStop;
import searchengine.exceptions.DuplicateException;
import searchengine.exceptions.WebException;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RecursiveAction;

@Component
public class WebsiteFJPServiceImpl extends RecursiveAction
        implements WebsiteFJPService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteModel siteModel;
    private final PageModel pageModel;

    String regexFiles = "^.*\\.(jpg|JPG|gif|GIF|doc|DOC|pdf|PDF)$";
    String regexWebsite;
    private final LemmaUtil lemmaUtil;
    private final IndexingStop indexingStop;

    private final ModelProcessingUtil modelProcessingUtil;


    @Autowired
    public WebsiteFJPServiceImpl(SiteRepository siteRepository,
                                 PageRepository pageRepository,
                                 SiteModel siteModel,
                                 PageModel pageModel,
                                 LemmaUtil lemmaUtil,
                                 IndexingStop indexingStop,
                                 ModelProcessingUtil modelProcessingUtil) {
        this.pageModel = pageModel;
        this.siteRepository = siteRepository;
        this.siteModel = siteModel;
        this.pageRepository = pageRepository;
        this.lemmaUtil = lemmaUtil;
        this.indexingStop = indexingStop;
        regexWebsite = siteModel.getUrl() + "[^#]*";
        this.modelProcessingUtil = modelProcessingUtil;
    }

    @Override
    public void compute() {

        if (indexingStop.isStopIndexingFlag()) {
            Thread.currentThread().interrupt();
        }

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<PageModel, Elements> pageModelResponseMap = setAllModelsForPageModel(siteModel, pageModel);

        if (pageModelResponseMap == null) {
            return;
        }

        addPathsToTaskPool(pageModelResponseMap.entrySet()
                .iterator().next().getValue());
    }

    public Map<PageModel, Elements> setAllModelsForPageModel(SiteModel siteModel,
                                                             PageModel pageModel) {

        Map<PageModel, Elements> pageModelResponseMap;
        try {
            pageModelResponseMap = modelProcessingUtil
                    .createOrUpdatePageModel(siteModel, pageModel.getPath(), pageModel);
            pageModel = pageModelResponseMap.entrySet().iterator().next().getKey();
            siteModel = modelProcessingUtil.setStatusTimeToSiteModel(siteModel);
            lemmaUtil.addToLemmaAndIndexTables(pageModel.getContent(), siteModel, pageModel);
        } catch (IOException | DuplicateException | WebException | RuntimeException e) {
            modelProcessingUtil.checkIfThePageIsTheMainPage(siteModel, pageModel, e);
            return null;
        }
        return pageModelResponseMap;
    }


    public void addPathsToTaskPool(Elements links) {

        for (Element link : links) {

            if (indexingStop.isStopIndexingFlag()) {
                Thread.currentThread().interrupt();
            }

            String path = link.absUrl("href");

            if (!(path.matches(regexWebsite)) ||
                    (path.matches(regexFiles))) {
                continue;
            }

            WebsiteFJPServiceImpl recursiveWebsiteMap =
                    new WebsiteFJPServiceImpl(siteRepository, pageRepository, siteModel,
                            modelProcessingUtil.setPathToPageModel(path),
                            lemmaUtil, indexingStop, modelProcessingUtil);

            recursiveWebsiteMap.fork();
        }

    }
}