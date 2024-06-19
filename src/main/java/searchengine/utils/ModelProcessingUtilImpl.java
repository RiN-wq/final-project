package searchengine.utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
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

@Service
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
            IOException, DuplicateException, WebException {

        pageModel.setSiteModel(siteModel);
        pageModel.setPath(path);

        Connection.Response connection = getConnection(path);

        pageModel.setCode(connection.statusCode());

        throwExceptionByStatusCode(pageModel.getCode());

        pageModel.setContent(connection.body());

        synchronized (pageRepository) {
            if (pageRepository.findByPath(pageModel.getPath()) != null) {
                throw new DuplicateException("Попытка вставки дубликата");
            }

            pageRepository.save(pageModel);
        }

        return Collections.singletonMap(pageModel, connection.parse().select("a"));

    }

    public PageModel createOrUpdatePageModel(SiteModel siteModel,
                                             String path) throws
            IOException, DuplicateException, WebException {

        return createOrUpdatePageModel
                (siteModel, path, new PageModel()).entrySet().iterator().next().getKey();

    }

    public void checkIfThePageIsTheMainPage(SiteModel siteModel,
                                            PageModel pageModel,
                                            Exception e) {

        if (siteModel.getUrl().equals(pageModel.getPath()) && !(e instanceof DuplicateException)) {
            siteModel.setLastError("Сайт недоступен, ошибка главной страницы:\"" + e.toString() + "\"");
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
            return e.toString();
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
                .referrer("http://www.google.com").ignoreHttpErrors(true).timeout(4000).execute();
    }

    public LemmaModel createOrUpdateLemmaModel(SiteModel siteModel,
                                               String lemma,
                                               LemmaModel lemmaModel) {

        lemmaModel.setSiteModel(siteModel);
        lemmaModel.setLemma(lemma);
        lemmaModel = synchronizedAddFrequency(lemmaModel, lemma);

        return lemmaModel;

    }

    synchronized public LemmaModel synchronizedAddFrequency(LemmaModel lemmaModel, String lemma) {

        if (lemmaRepository.findByLemma(lemma) != null) {
            lemmaModel = lemmaRepository.findByLemma(lemma);
            lemmaModel.setFrequency(lemmaModel.getFrequency() + 1);
        } else {
            lemmaModel.setFrequency(1);
        }

        lemmaRepository.save(lemmaModel);
        return lemmaModel;
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

    public void clearTables(SiteModel siteModel) {

        List<PageModel> pageModelList = pageRepository.findAllBySiteModel(siteModel);

        if (pageModelList.isEmpty()) {
            return;
        }


        for (PageModel pageModel : pageModelList) {

            if (indexingStop.isStopIndexingFlag()) {
                return;
            }

            clearLemmaAndIndexTablesByPageModel(indexRepository.findAllByPageModel(pageModel));

            pageRepository.delete(pageModel);
        }

        siteRepository.delete(siteModel);

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
