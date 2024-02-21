package searchengine.services;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingStop;
import searchengine.exceptions.ClientException;
import searchengine.exceptions.DuplicateException;
import searchengine.exceptions.RedirectionException;
import searchengine.exceptions.ServerException;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RecursiveAction;

@Service
public class WebsiteFJPServiceImpl extends RecursiveAction
        implements WebsiteFJPService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteModel siteModel;
    private final PageModel pageModel;

    String regexFiles = "^.*\\.(jpg|JPG|gif|GIF|doc|DOC|pdf|PDF)$";
    String regexWebsite;
    private final LemmaService lemmaService;
    private final IndexingStop indexingStop;

    private final ModelProcessingService modelProcessingService;


    @Autowired
    public WebsiteFJPServiceImpl(SiteRepository siteRepository,
                                 PageRepository pageRepository,
                                 SiteModel siteModel,
                                 PageModel pageModel,
                                 LemmaService lemmaService,
                                 IndexingStop indexingStop,
                                 ModelProcessingService modelProcessingService) {
        this.pageModel = pageModel;
        this.siteRepository = siteRepository;
        this.siteModel = siteModel;
        this.pageRepository = pageRepository;
        this.lemmaService = lemmaService;
        this.indexingStop = indexingStop;
        regexWebsite = siteModel.getUrl() + "[^#]*";
        this.modelProcessingService = modelProcessingService;
    }

    @Override
    public void compute() {

        if (indexingStop.isStopIndexingFlag()) {
            Thread.currentThread().interrupt();
        }

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            System.err.println(e.toString());
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
            pageModelResponseMap = modelProcessingService
                    .createOrUpdatePageModel(siteModel, pageModel.getPath(), pageModel);
            pageModel = pageModelResponseMap.entrySet().iterator().next().getKey();
            siteModel = modelProcessingService.setStatusTimeToSiteModel(siteModel);
            lemmaService.addToLemmaAndIndexTables(pageModel.getContent(), siteModel, pageModel);
        } catch (IOException | DuplicateException | RedirectionException |
                 ClientException | ServerException | RuntimeException e) {
            modelProcessingService.checkIfThePageIsTheMainPage(siteModel, pageModel, e);
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
                            modelProcessingService.setPathToPageModel(path),
                            lemmaService, indexingStop, modelProcessingService); // рекурсивный вызов класса

            recursiveWebsiteMap.fork(); // отправление задачи в очередь ПОТОКА (НО НЕ ЗАПУСК ВЫПОЛНЕНИЯ)
        }

    }
}