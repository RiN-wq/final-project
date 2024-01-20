package searchengine.services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingServiceImpl implements IndexingService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private SiteModel siteModel;
    private PageModel pageModel;
    private final SitesList sitesList;
    private List<Site> siteList = null;
    ArrayList<String> response;
    ArrayList<String> anotherResponse;
    ArrayList<String> responseOfStopMethod;
    ForkJoinPool forkJoinPool;
    boolean stopIndexingFlag;
    boolean indexInProcessFlag;
    private final WebsiteRecursionServiceImpl websiteRecursionServiceImpl;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository,
                               PageRepository pageRepository,
                               SiteModel siteModel,
                               PageModel pageModel,
                               WebsiteRecursionServiceImpl websiteRecursionServiceImpl,
                               SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.siteModel = siteModel;
        this.pageModel = pageModel;
        this.websiteRecursionServiceImpl = websiteRecursionServiceImpl;
        this.sitesList = sitesList;
    }

    @Override
    public List<String> indexAllSites() {

        List<Site> sites = siteList != null ? siteList : sitesList.getSites();

        // если вдруг выполняется массовая индексация
        if (indexInProcessFlag) {
            String error = "Индексация уже запущена!";
            return getResponse(true, error);
        }
        indexInProcessFlag = true;

        response = new ArrayList<>();
        forkJoinPool = new ForkJoinPool();
        clearSitesFromTable(sites);

        while (!startIndexing(sites)){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        // проверка, завершился он по доброй воле или его принУдили
        if (!forkJoinPool.isShutdown()){
            forkJoinPool.shutdownNow();
        } else {
            stopIndexingFlag = false;
        }

        indexInProcessFlag = false;
        return response;
    }

    public List<String> indexPage(Site site) {
        anotherResponse = new ArrayList<>();

        siteList = List.of(site);
        anotherResponse = (ArrayList<String>) indexAllSites();
        siteList = null;

        return anotherResponse;

    }

    public boolean startIndexing(List<Site> sitesList){

        for (Site siteIterator : sitesList) {
            String url = siteIterator.getUrl();
            String name = siteIterator.getName();

            siteModel = new SiteModel();

            if (stopIndexingFlag){
                siteModel = createOrUpdateSite(url, name, Status.FAILED);
                siteRepository.save(siteModel);

                response.addAll(getResponse(stopIndexingFlag, siteModel.getLastError()));
                continue;
            }

            siteModel = createOrUpdateSite(url, name, Status.INDEXING);
            siteRepository.save(siteModel);

            pageModel.setPath(url);

            forkJoinPool.invoke(new WebsiteRecursionServiceImpl(siteRepository,
                    pageRepository, siteModel, pageModel));

            while (!forkJoinPool.isQuiescent()){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            siteModel = (stopIndexingFlag || siteModel.getLastError() != null) ?
                    createOrUpdateSite(url, name, Status.FAILED): createOrUpdateSite(url, name, Status.INDEXED);
            siteRepository.save(siteModel);

            response.addAll(getResponse(stopIndexingFlag, siteModel.getLastError()));
        }
        return true;
    }

    public boolean startIndexing(Site site){
        List<Site> siteList = List.of(site);
        return startIndexing(siteList);
    }


    public void clearSitesFromTable(List<Site> sites){

        for (Site siteIterator : sites) {

            String url = siteIterator.getUrl();

            if (siteRepository.findByUrl(url) != null) {

                siteModel = siteRepository.findByUrl(url);
                int siteId = siteModel.getId();

                pageRepository.deleteAll(pageRepository.findBySiteModel(siteModel));
                siteRepository.deleteById(siteId);

            }
        }
    }

    public void clearSitesFromTable(Site site){
        List<Site> sites = List.of(site);
        clearSitesFromTable(sites);
    }


    public List<String> stopIndexing(){
        responseOfStopMethod = new ArrayList<>();

        String result;
        String error;
        if (forkJoinPool == null || forkJoinPool.isShutdown()){
            result = "false";
            error = "Индексация не запущена";

            responseOfStopMethod.add(result);
            responseOfStopMethod.add(error);

            return responseOfStopMethod;
        }
        stopIndexingFlag = true;

        forkJoinPool.shutdownNow();

        result = "true";

        responseOfStopMethod.add(result);

        return responseOfStopMethod;
    }


    public List<String> getResponse(boolean errorCheckerFlag,
                                    String error){

        List<String> partOfResponse = new ArrayList<>();
        String result;

        if (errorCheckerFlag){
            result = "false";

            partOfResponse.add(result);
            partOfResponse.add(error);
            return partOfResponse;
        }
        result = "true";
        partOfResponse.add(result);
        return partOfResponse;

    }

    public SiteModel createOrUpdateSite(String url,
                                        String name,
                                        Status status){
        siteModel.setUrl(url);
        siteModel.setName(name);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setStatus(status);

        if (status.equals(Status.FAILED) && siteModel.getLastError() == null){
            siteModel.setLastError("Индексация остановлена пользователем");
        }
        return siteModel;
    }
}