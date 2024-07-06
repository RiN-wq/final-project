package searchengine.utils;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.dto.indexing.IndexingStop;
import searchengine.exceptions.*;
import searchengine.models.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;

@Component
public class ModelProcessingUtilImpl implements ModelProcessingUtil {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingStop indexingStop;
    public ModelProcessingUtilImpl(SiteRepository siteRepository,
                                   PageRepository pageRepository,
                                   LemmaRepository lemmaRepository,
                                   IndexRepository indexRepository,
                                   IndexingStop indexingStop) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.indexingStop = indexingStop;
    }

    private HashSet<String> pageModelSet;

    public void initializeSetOfPaths(List<Site> sites){
        pageModelSet = new HashSet<>();
        sites.forEach(site -> writeCheckedPath(site.getUrl()));
    }

    public boolean isPathChecked(String path){
        return pageModelSet.contains(path);
    }

    public synchronized boolean writeCheckedPath(String path){
        return pageModelSet.add(path);
    }

    public SiteModel createOrUpdateSiteModel(Site site,
                                             Status status,
                                             SiteModel siteModel) {
        siteModel.setUrl(site.getUrl());
        siteModel.setName(site.getName());
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setStatus(status);

        if (status.equals(Status.FAILED) && siteModel.getLastError() == null) {
            siteModel.setLastError("Индексация остановлена пользователем");
        }

        siteRepository.save(siteModel);

        return siteModel;

    }

    public SiteModel setStatusTimeToSiteModel(SiteModel siteModel) {

        siteModel.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteModel);
        return siteModel;

    }

    public Map<PageModel, Elements> createOrUpdatePageModel(SiteModel siteModel,
                                                            String path,
                                                            PageModel pageModel) throws
            IOException, WebException {

        pageModel.setSiteModel(siteModel);
        pageModel.setPath(path);

        Connection.Response connection = getConnection(path);

        pageModel.setCode(connection.statusCode());

        throwExceptionByStatusCode(pageModel.getCode());

        pageModel.setContent(connection.body());

        pageRepository.save(pageModel);

        return Collections.singletonMap(pageModel, connection.parse().select("a"));

    }

    public PageModel createOrUpdatePageModel(SiteModel siteModel,
                                             String path) throws
            IOException, WebException {

        return createOrUpdatePageModel
                (siteModel, path, new PageModel()).entrySet().iterator().next().getKey();

    }

    public void checkIfThePageIsTheMainPage(SiteModel siteModel,
                                            String path,
                                            Exception e) {

        if (siteModel.getUrl().equals(path) && !(e instanceof DuplicateException)
                && !(e instanceof OptimisticLockingFailureException) && !(e instanceof PersistenceException)
                && !(e instanceof InvalidDataAccessApiUsageException)) {
            siteModel.setLastError("Сайт недоступен, ошибка главной страницы: \"" + e.getMessage() + "\"");
            createOrUpdateSiteModel(new Site(siteModel.getUrl(), siteModel.getName()), Status.FAILED, siteModel);
        }

    }

    public void throwExceptionByStatusCode(int statusCode)
            throws WebException {

        switch (String.valueOf(statusCode).substring(0, 1)) {
            case "2":
                return;
            case "3":
                throw new RedirectionException("Ошибка перенаправления");
            case "4":
                getClientException(statusCode);
            case "5":
                throw new ServerException("Ошибка на стороне сервера");
            default:
                throw new RuntimeException("Неизвестная ошибка");
        }

    }

    public void getClientException(int statusCode) throws ClientException {

        switch (statusCode) {
            case 400:
                throw new ClientException("Некорректный запрос");
            case 401:
                throw new ClientException("Не авторизован");
            case 403:
                throw new ClientException("Доступ запрещён");
            case 404:
                throw new ClientException("Не найдено");
            case 405:
                throw new ClientException("Метод не поддерживается");
            default:
                throw new ClientException("Ошибка на стороне клиента");
        }

    }

    public String getPageModelExceptionMessage(Exception e) {

        if (e instanceof IOException) {
            return "Ошибка подключения к странице сайта";
        } else if (e instanceof DuplicateException) {
            return "Попытка добавление дубликата";
        } else if (e instanceof RedirectionException ||
                e instanceof ClientException ||
                e instanceof ServerException ||
                e instanceof RuntimeException) {
            return e.getMessage();
        }

        return "error";
    }

    public PageModel setPathToPageModel(String path) {

        PageModel pageModel = new PageModel();
        pageModel.setPath(path);
        return pageModel;

    }

    public Connection.Response getConnection(String path) throws IOException {

        return Jsoup.connect(path)
                .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                .referrer("http://www.google.com").ignoreHttpErrors(true).timeout(3000).execute();
    }

    public LemmaModel createOrUpdateLemmaModel(SiteModel siteModel,
                                               String lemma,
                                               LemmaModel lemmaModel) {

        lemmaModel.setSiteModel(siteModel);
        lemmaModel.setLemma(lemma);
        try {
            lemmaModel = optimisticLockAddFrequency(lemmaModel, lemma);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }

        return lemmaModel;

    }

    public LemmaModel optimisticLockAddFrequency(LemmaModel lemmaModel, String lemma)
            throws InterruptedException {

        try {
            if (lemmaRepository.findByLemma(lemma) != null) {
                lemmaModel = lemmaRepository.findByLemma(lemma);
                lemmaModel.setFrequency(lemmaModel.getFrequency() + 1);
            } else {
                lemmaModel.setFrequency(1);
            }

            lemmaRepository.save(lemmaModel);
            return lemmaModel;

        } catch (OptimisticLockException e){
            optimisticLockAddFrequency(lemmaModel, lemma);
        }

        return null;
    }

    public void saveIndexModel(PageModel pageModel,
                               LemmaModel lemmaModel,
                               float rank) {

        IndexModel indexModel = new IndexModel();

        indexModel.setLemmaModel(lemmaModel);
        indexModel.setPageModel(pageModel);
        indexModel.setRank(rank);

        indexRepository.save(indexModel);

    }

    public void clearTables(SiteModel siteModel, CountDownLatch countDownLatchOfClear) {

        if (siteModel != null){

            List<PageModel> pageModelList = pageRepository.findAllBySiteModel(siteModel);
            List<IndexModel> batchForDeleting = new ArrayList<>();

            deleteIndexesInBatch(pageModelList, batchForDeleting);

            pageRepository.deleteAllInBatch(pageModelList);

            countDownLatchOfClear.countDown();
            try {
                countDownLatchOfClear.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            lemmaRepository.deleteAllInBatch(lemmaRepository.findAllBySiteModel(siteModel));

            siteRepository.delete(siteModel);
        }

    }

    public void deleteIndexesInBatch(List<PageModel> pageModelList, List<IndexModel> batchForDeleting){

        for (int i = 0; i < pageModelList.size(); i++) {
            if (indexingStop.isStopIndexingFlag()){
                return;
            }
            batchForDeleting.addAll(indexRepository.findAllByPageModel(pageModelList.get(i)));

            if (i != 0 && i % 12 == 0){
                indexRepository.flush();
                indexRepository.deleteAllInBatch(batchForDeleting);
                batchForDeleting.clear();
            }
        }

        indexRepository.deleteAllInBatch(batchForDeleting);

    }

    public void clearTables(PageModel pageModel) {

        if (pageModel == null) {
            return;
        }

        clearLemmaAndIndexTablesByPageModel(indexRepository.findAllByPageModel(pageModel));
        pageRepository.delete(pageModel);

    }

    public void clearLemmaAndIndexTablesByPageModel(List<IndexModel> indexModelList) {

        LinkedList<LemmaModel> lemmasForDeleting = new LinkedList<>();
        LinkedList<LemmaModel> lemmasForUpdating = new LinkedList<>();
        LinkedList<IndexModel> indexesOfDeletingLemmas = new LinkedList<>();
        LinkedList<IndexModel> indexesOfUpdatingLemmas = new LinkedList<>();


        for (IndexModel indexModel : indexModelList) {

            LemmaModel lemmaModel = indexModel.getLemmaModel();

            if (lemmaModel.getFrequency() == 1) {
                lemmasForDeleting.add(lemmaModel);
                indexesOfDeletingLemmas.add(indexModel);
            } else {
                lemmaModel.setFrequency(lemmaModel.getFrequency() - 1);
                lemmasForUpdating.add(lemmaModel);
                indexesOfUpdatingLemmas.add(indexModel);
            }

        }

        deleteOrSaveInBatch(lemmasForDeleting, indexesOfDeletingLemmas, true);
        deleteOrSaveInBatch(lemmasForUpdating, indexesOfUpdatingLemmas, false);

    }

    public void deleteOrSaveInBatch(List<LemmaModel> lemmasForProcessing,
                                    List<IndexModel> indexesForProcessing,
                                    boolean deletingFlag) {

        if (lemmasForProcessing.isEmpty()) {
            return;
        }

        int bufferSize;

        while (lemmasForProcessing != null) {
            bufferSize = Math.min(lemmasForProcessing.size(), 400);

            indexRepository.deleteAllInBatch(indexesForProcessing.subList(0, bufferSize));

            if (deletingFlag) {
                lemmaRepository.deleteAllInBatch(lemmasForProcessing.subList(0, bufferSize));
            } else {
                lemmaRepository.saveAll(lemmasForProcessing.subList(0, bufferSize));
            }

            if (lemmasForProcessing.size() > bufferSize) {
                lemmasForProcessing = lemmasForProcessing.subList(bufferSize, lemmasForProcessing.size());
                indexesForProcessing = indexesForProcessing.subList(bufferSize, indexesForProcessing.size());
            } else {
                lemmasForProcessing = null;
            }

        }
    }

}
