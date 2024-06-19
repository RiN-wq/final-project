package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingStop;
import searchengine.dto.responses.Response;
import searchengine.dto.responses.SimpleErrorResponse;
import searchengine.dto.responses.SimpleSuccessfulResponse;
import searchengine.exceptions.DuplicateException;
import searchengine.exceptions.InvalidInputException;
import searchengine.exceptions.WebException;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;
import searchengine.models.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaUtil;
import searchengine.utils.ModelProcessingUtil;
import searchengine.utils.WebsiteFJPServiceImpl;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndexingServiceImpl implements IndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;
    ForkJoinPool forkJoinPool;
    boolean indexingFlag;
    private final ModelProcessingUtil modelProcessingUtil;
    private final SimpleErrorResponse simpleErrorResponse;
    private final SimpleSuccessfulResponse simpleSuccessfulResponse;
    private final LemmaUtil lemmaUtil;
    private final IndexingStop indexingStop;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository,
                               PageRepository pageRepository,
                               LemmaRepository lemmaRepository,
                               SitesList sitesList,
                               ModelProcessingUtil modelProcessingUtil,
                               SimpleErrorResponse simpleErrorResponse,
                               SimpleSuccessfulResponse simpleSuccessfulResponse,
                               LemmaUtil lemmaUtil,
                               IndexingStop indexingStop) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.sitesList = sitesList;
        this.modelProcessingUtil = modelProcessingUtil;
        this.simpleErrorResponse = simpleErrorResponse;
        this.simpleSuccessfulResponse = simpleSuccessfulResponse;
        this.lemmaUtil = lemmaUtil;
        this.indexingStop = indexingStop;
    }

    @Override
    public Response indexAllSites() {

        if (isIndexingGoingOnNow()) {
            return getResponse("Индексация уже запущена");
        }

        List<Site> sites = sitesList.getSites();

        forkJoinPool = new ForkJoinPool();

        for (Site site : sites) {
            modelProcessingUtil.clearTables(siteRepository.findByUrl(site.getUrl()));
        }

        try {
            startIndexing(sites);
        } catch (InterruptedException e) {
            System.err.println(e.toString());
        }

        indexingFlag = false;
        return getResponse("");
    }

    public boolean isIndexingGoingOnNow() {

        if (indexingFlag) {
            return true;
        }

        indexingFlag = true;
        indexingStop.setStopIndexingFlag(false);

        return false;

    }


    public void startIndexing(List<Site> sitesList) throws InterruptedException {
        for (Site siteIterator : sitesList) {
            SiteModel siteModel = new SiteModel();

            if (indexingStop.isStopIndexingFlag()) {
                modelProcessingUtil.createOrUpdateSiteModel(siteIterator, Status.FAILED, siteModel);
                continue;
            }

            siteModel = modelProcessingUtil.createOrUpdateSiteModel(siteIterator, Status.INDEXING, siteModel);

            forkJoinPool.invoke(new WebsiteFJPServiceImpl(siteRepository, pageRepository, siteModel,
                    modelProcessingUtil.setPathToPageModel(siteIterator.getUrl()),
                    lemmaUtil, indexingStop, modelProcessingUtil));

            while (!forkJoinPool.isQuiescent()) {
                Thread.sleep(1000);
            }

            modelProcessingUtil.createOrUpdateSiteModel(siteIterator,
                    (indexingStop.isStopIndexingFlag() || siteModel.getLastError() != null) ?
                            Status.FAILED : Status.INDEXED, siteModel);
        }
    }


    public Response indexPage(String path) {
        if (isIndexingGoingOnNow()) {
            return getResponse("Индексация уже запущена");
        }
        SiteModel siteModel;
        PageModel pageModel = null;

        try {
            path = getCorrectPathForm(path);
            siteModel = findOrCreateSiteByPagePath(path);
        } catch (InvalidInputException e) {
            indexingFlag = false;
            return getResponse(e.toString());
        }
        modelProcessingUtil.clearTables(pageRepository.findByPath(path));

        try {
            pageModel = modelProcessingUtil.createOrUpdatePageModel(siteModel, path);
            lemmaUtil.addToLemmaAndIndexTables(pageModel.getContent(), siteModel, pageModel);
        } catch (IOException | DuplicateException | WebException | RuntimeException e) {
            modelProcessingUtil.checkIfThePageIsTheMainPage(siteModel, pageModel, e);
            indexingFlag = false;
            return getResponse(modelProcessingUtil.getPageModelExceptionMessage(e));
        }

        indexingFlag = false;
        return getResponse("");
    }


    public String getCorrectPathForm(String path) throws InvalidInputException {

        path = path.replace("url=", "")
                .replace("%2F", "/")
                .replace("%3A", ":");

        Pattern urlPattern = Pattern.compile("((http|https)://(www.)?[a-z0-9-_]+\\.[^.\" \n\t]+)");
        Matcher matcher = urlPattern.matcher(path.toLowerCase());

        while (matcher.find()) {
            return matcher.group(1);
        }

        throw new InvalidInputException("Ошибка ввода");

    }

    public SiteModel findOrCreateSiteByPagePath(String path) throws InvalidInputException {

        SiteModel siteModel = new SiteModel();

        List<Site> matchingSite = new ArrayList<>();

        for (Site site : sitesList.getSites()) {
            String siteDomainName = site.getUrl().replaceAll("(http:|https:)", "")
                    .replace("/", "");

            if (path.matches("[^#]*" + siteDomainName + "[^#]*")) {
                matchingSite.add(site);
            }
        }

        if (matchingSite.size() != 1) {
            throw new InvalidInputException("Количество подходящий сайтов из файла конфигурации не равно 1");
        }
        String siteUrl = matchingSite.get(0).getUrl();

        siteModel = modelProcessingUtil.createOrUpdateSiteModel(matchingSite.get(0),
                siteRepository.findByUrl(siteUrl) == null ? Status.INDEXING
                        : siteRepository.findByUrl(siteUrl).getStatus(),
                siteRepository.findByUrl(siteUrl) == null ? siteModel
                        : siteRepository.findByUrl(siteUrl));

        return siteModel;
    }


    public Response stopIndexing() {

        if (forkJoinPool == null || forkJoinPool.isShutdown()) {
            return getResponse("Индексация ещё не запущена");
        }

        forkJoinPool.shutdownNow();
        indexingStop.setStopIndexingFlag(true);

        return getResponse("");
    }

    public Response getResponse(String error) {

        if (error.isBlank()){
            return simpleSuccessfulResponse;
        } else {
            simpleErrorResponse.setError(error);
            return simpleErrorResponse;
        }

    }

    public boolean getIndexingStatus() {
        return indexingFlag;
    }

    public int getPagesCountOfSite(Site site) {
        return pageRepository.countBySiteModel(siteRepository.findByUrl(site.getUrl()));
    }

    public int getLemmasCountOfSite(Site site) {
        return lemmaRepository.countBySiteModel(siteRepository.findByUrl(site.getUrl()));
    }

    public String getSiteStatus(Site site) {
        if (siteRepository.findByUrl(site.getUrl()) == null) {
            return Status.INDEXING.toString();
        }

        return siteRepository.findByUrl(site.getUrl()).getStatus().toString();
    }

    public long getSiteStatusTime(Site site) {

        if (siteRepository.findByUrl(site.getUrl()) == null) {
            return new Date().getTime();
        }

        return siteRepository.findByUrl(site.getUrl()).getStatusTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

    }

    public String getSiteError(Site site) {

        if (siteRepository.findByUrl(site.getUrl()) == null ||
                siteRepository.findByUrl(site.getUrl()).getLastError() == null) {
            return "";
        }

        return siteRepository.findByUrl(site.getUrl()).getLastError();

    }

}