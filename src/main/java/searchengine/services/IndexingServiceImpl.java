package searchengine.services;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingStop;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingServiceImpl implements IndexingService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private SiteModel siteModel;
    private SiteModel siteWithIndexingPage;
    private final PageModel pageModel;
    private LemmaModel lemmaModel;
    private IndexModel indexModel;
    private final SitesList sitesList;
    ArrayList<String> response;
    ForkJoinPool forkJoinPool;
    boolean siteIndexProcessFlag;
    boolean pageIndexProcessFlag;
    int statusCode;
    private final WebsiteFJPServiceImpl websiteRecursionServiceImpl;
    private final LemmaService lemmaService;
    private final IndexingStop indexingStop;

    @Autowired
    public IndexingServiceImpl(SiteRepository siteRepository,
                               PageRepository pageRepository,
                               LemmaRepository lemmaRepository,
                               IndexRepository indexRepository,
                               SiteModel siteModel,
                               PageModel pageModel,
                               LemmaModel lemmaModel,
                               IndexModel indexModel,
                               WebsiteFJPServiceImpl websiteRecursionServiceImpl,
                               SitesList sitesList,
                               LemmaService lemmaService,
                               IndexingStop indexingStop) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.siteModel = siteModel;
        this.pageModel = pageModel;
        this.lemmaModel = lemmaModel;
        this.indexModel = indexModel;
        this.websiteRecursionServiceImpl = websiteRecursionServiceImpl;
        this.sitesList = sitesList;
        this.lemmaService = lemmaService;
        this.indexingStop = indexingStop;
    }

    @Override
    public List<String> indexAllSites() {

        // если вдруг выполняется какая-либо индексация
        if (siteIndexProcessFlag || pageIndexProcessFlag) {
            return getResponse(true, "Индексация уже запущена");
        }

        siteIndexProcessFlag = true;
        indexingStop.setStopIndexingFlag(false);

        List<Site> sites = sitesList.getSites();

        response = new ArrayList<>();
        forkJoinPool = new ForkJoinPool();

        for (Site site : sites) {
            clearTables(siteRepository.findByUrl(site.getUrl()));
        }

        while (!startIndexing(sites)){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (!forkJoinPool.isShutdown()){
            forkJoinPool.shutdownNow();
        }

        siteIndexProcessFlag = false;
        return response;
    }


    public boolean startIndexing(List<Site> sitesList){
        for (Site siteIterator : sitesList) {

            String url = siteIterator.getUrl();
            String name = siteIterator.getName();

            siteModel = new SiteModel();

            if (indexingStop.isStopIndexingFlag()){
                siteModel = createOrUpdateSite(url, name, Status.FAILED);
                siteRepository.save(siteModel);

                response.addAll(getResponse(indexingStop.isStopIndexingFlag(), siteModel.getLastError()));
                continue;
            }

            siteModel = createOrUpdateSite(url, name, Status.INDEXING);
            siteRepository.save(siteModel);

            pageModel.setPath(url);

            forkJoinPool.invoke(new WebsiteFJPServiceImpl(siteRepository,
                    pageRepository, siteModel, pageModel, lemmaService, indexingStop));

            while (!forkJoinPool.isQuiescent()){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            siteModel = (indexingStop.isStopIndexingFlag() || siteModel.getLastError() != null) ?
                    createOrUpdateSite(url, name, Status.FAILED) : createOrUpdateSite(url, name, Status.INDEXED);
            siteRepository.save(siteModel);

            response.addAll(getResponse(indexingStop.isStopIndexingFlag(), siteModel.getLastError()));
        }
        return true;
    }

    @Override
    public void clearSiteAndPageTables(List<Site> sites) {

    }

    public List<String> indexPage(String path) {
        //TODO: Нормализовать получение ссылки через PostMan (т.е. через формат JSON)
        if (pageIndexProcessFlag || siteIndexProcessFlag){
            return getResponse(true, "Индексация уже запущена!");
        }

        indexingStop.setStopIndexingFlag(false);

        pageIndexProcessFlag = true;

        path = path.replace("url=","")
                .replace("%2F","/")
                .replace("%3A", ":");

        PageModel pageForIndexing = new PageModel();

        String url = checkSiteByPage(path);

        if (url != null){

            clearTables(pageModel);

            int statusCode = 400;
            String[] informationFromConnection;

            String content;

            pageForIndexing.setSiteModel(siteRepository.findByUrl(url));

            pageForIndexing.setPath(path);

            try {

                informationFromConnection = getConnection(path);

                statusCode = Integer.parseInt(informationFromConnection[0]);
                content = informationFromConnection[1];

                if (statusCode != 200){
                    throw new IOException();
                }

            } catch (IOException e) {
                pageForIndexing.setCode(statusCode);
                pageForIndexing.setContent("");

                pageRepository.save(pageModel);

                pageIndexProcessFlag = false;

                return (statusCode == 400 ? getResponse(true, "Указанная страница не существует") :
                        getResponse(true, "Не получилось подключиться"));
            }

            pageForIndexing.setCode(statusCode);
            pageForIndexing.setContent(content);

            pageRepository.save(pageForIndexing);

            lemmaService.addToLemmaAndIndexTables(content, siteWithIndexingPage, pageForIndexing);

            pageIndexProcessFlag = false;

            return getResponse(false, "");
        }

        pageIndexProcessFlag = false;

        return getResponse(true, "Данная страница находится за пределами сайтов,\n" +
                    "указанных в конфигурационном файле");
    }

    public String checkSiteByPage(String path){
        siteWithIndexingPage = new SiteModel();

        List<Site> sites = sitesList.getSites();

        for (Site site : sites) {
            String url = site.getUrl().strip();
            String regex = url + "[^#]*";

            if (path.matches(regex)){
                if (siteRepository.findByUrl(url) == null){
                    siteWithIndexingPage.setStatusTime(LocalDateTime.now());
                    siteWithIndexingPage.setName(url);
                    siteWithIndexingPage.setUrl(url);
                    siteWithIndexingPage.setStatus(Status.INDEXING);
                }
                else {
                    siteWithIndexingPage = siteRepository.findByUrl(url);
                }
                siteRepository.save(siteWithIndexingPage);

                return url;
            }
        }
        return null;
    }

/*    public void clearSiteAndPageTables(List<Site> sites){
        for (Site siteIterator : sites) {
            if (indexingStop.isStopIndexingFlag()){
                break;
            }


            String url = siteIterator.getUrl();

            if (siteRepository.findByUrl(url) != null) {

                siteModel = siteRepository.findByUrl(url);

                pageRepository.deleteAllInBatch(pageRepository.findAllBySiteModel(siteModel));
                    siteRepository.delete(siteModel);

            }
        }
    }*/

    public void sendListByBuffer(List<LemmaModel> lemmasForProcessing,
                                 List<IndexModel> indexesForProcessing,
                                 boolean deletingFlag){

        int bufferSize = 400;

        if (lemmasForProcessing.isEmpty()){
            return;
        }

        if (lemmasForProcessing.size() < bufferSize){
            bufferSize = lemmasForProcessing.size();
        }

        while (lemmasForProcessing != null){

            indexRepository.deleteAllInBatch(indexesForProcessing.subList(0, bufferSize - 1));

            //TODO: ПЕРЕД СЛЕД. ИЗМЕНЕНИЯМИ ЗАЛИТЬ ПРОЕКТ НА GITHUB (надоело искать ошибки днями)
            if (deletingFlag) {
                lemmaRepository.deleteAllInBatch(lemmasForProcessing.subList(0, bufferSize - 1));
            }
            else {
                lemmaRepository.saveAll(lemmasForProcessing.subList(0, bufferSize - 1));
            }

            lemmasForProcessing = lemmasForProcessing.size() > bufferSize ?
                    lemmasForProcessing.subList(bufferSize, lemmasForProcessing.size() - 1) :
                    null;
        }

    }

    public void processIndex(List<IndexModel> indexModelList){


        /*Map<LemmaModel, IndexModel> lemmasForDeleting;
        Map<LemmaModel, IndexModel> lemmasForUpdating;*/

        LinkedList<LemmaModel> lemmasForDeleting = new LinkedList<>();
        LinkedList<LemmaModel> lemmasForUpdating = new LinkedList<>();
        LinkedList<IndexModel> indexesOfDeletingLemmas = new LinkedList<>();
        LinkedList<IndexModel> indexesOfUpdatingLemmas = new LinkedList<>();


        for(IndexModel indexModel : indexModelList){
            LemmaModel modelOfLemma = indexModel.getLemmaModel();

            if (indexModel.getLemmaModel().getFrequency() == 1){
                lemmasForDeleting.add(modelOfLemma);
                indexesOfDeletingLemmas.add(indexModel);
            }else {
                modelOfLemma.setFrequency(modelOfLemma.getFrequency() - 1);
                lemmasForUpdating.add(modelOfLemma);
                indexesOfUpdatingLemmas.add(indexModel);
            }

        }

  /*      lemmasForDeleting = indexModelList.stream()
                .filter(indexModel -> indexModel.getLemmaModel().getFrequency() == 1)
                .collect(Collectors.toMap(IndexModel::getLemmaModel, indexModel -> indexModel));


        lemmasForUpdating = indexModelList.stream()
                .map(IndexModel::getLemmaModel)
                .filter(lemmaModel -> lemmaModel.getFrequency() > 1)
                .peek(lemmaModel -> lemmaModel.setFrequency(lemmaModel.getFrequency() - 1))
                .collect(Collectors.toMap(lemmaModel -> lemmaModel, null));*/

        //indexRepository.deleteAllInBatch(indexModelList);

        sendListByBuffer(lemmasForDeleting, indexesOfDeletingLemmas, true);
        sendListByBuffer(lemmasForUpdating, indexesOfUpdatingLemmas, false);

/*        for(IndexModel indexModel : indexModelList){
            СДЕЛАТЬ 2 СТРИМА ПОЛУЧЕНИЯ LEMMASFOR DELETING И ... И ПОТОМ СНАЧАЛА УДАЛИТЬ ВСЕ ИНДЕКСЫ
                    СВЯЗАННЫЕ С ТЕКУЩЕЙ СТРАНИЦЕЙ А ЗАТЕМ ОБРАБОТАТЬ ПОЛУЧЕННЫЕ 2 ЛИСТА (ПО БУФЕРАМ), ВСЁ КРУТО ПОЛУЧИТСЯ
                    И ДАЖЕ НЕ ПОНАДОБИТСЯ ДЕЛАТЬ ВЛОЖЕННЫЕ ЦИКЛЫ



            if (indexModel.getLemmaModel().getFrequency() == 1){
                lemmasForDeleting.add(indexModel.getLemmaModel());
            }else {
                lemmaModel.setFrequency(lemmaModel.getFrequency() - 1);
                lemmasForUpdating.add(indexModel.getLemmaModel());
            }

        }


        ИНДЕКСЫ МЫ ТОЖЕ ДОЛЖНЫ УДАЛЯТЬ, ДОБАВИТЬ СИСТЕМУ ИХ УДАЛЕНИЯ!!!

        if (lemmasForUpdating.size() == 400 || крайняя итерация для indexModelList){
            lemmaRepository.deleteAllInBatch(lemmasForDeleting);
            lemmasForUpdating.clear();
        } else if (lemmasForDeleting.size() == 400 || крайняя итерация для...) {
            lemmaRepository.saveAll(lemmasForUpdating);
            lemmasForDeleting.clear();
        }*/

    }

   //TODO: Расклад таков: видимо есть дубликаты в таблице indexTable => не могут нормально и адекватно удалиться некоторые Enity

    public void clearTables(SiteModel siteModel){

        // сначала удаляем всё из indexTable путём итерации по pageModelList и запросами deleteAllInBatch
        // потом находим сайт, к которому привязаны страницы и понижаем частоту в таблице lemmaTable у всех подходящих лемм

        List<PageModel> pageModelList = pageRepository.findAllBySiteModel(siteModel);

        if (pageModelList.isEmpty()) {
            return;
        }


        for(PageModel pageModel : pageModelList){

            if(indexingStop.isStopIndexingFlag()){
                return;
            }

            processIndex(indexRepository.findAllByPageModel(pageModel));

            pageRepository.delete(pageModel);
        }

        siteRepository.delete(siteModel);

/*



        //index Table содержит связь страницы и сайта, именно на него нужно давить
        ПОЛУЧАЮ весь список индексов (получим всё, а далее отсеим ненужное)
                страницы загоняю в Map, где ключ - страница, а значения - indexModel


                ПРОГОНЯЮ ВЕСЬ СПИСОК ИНДЕКСОВ, постепенно заполняя Map со страницам (слишком ресурсозатратно)

                ДАЛЕЕ ДЛЯ КАЖДОЙ СТРАНИЦЫ ПРОГОНЯЮ: СТРАНИЦА - ЛЕММА, Т.Е. УМЕНЬШАЮ ЛЕММУ ИЛИ ВОВСЕ УДАЛЯЮ
                КОЛИЧЕСТВО СОХРАНЕНИЕ И УДАЛЕНИЙ ПАКЕТОМ ПОЛУЧИТСЯ МЕНЬШЕ, ЧЕМ ПРИ ЕДИНИЧНОМ РАСКЛАДЕ!!!
            ВСЁ РАВНО ДОБАВИТЬ ПРОВЕРКУ НА ОГРАНИЧЕНИЕ УДАЛЕНИЙ/ДОБАВЛЕНИЙ ПАКЕТОМ В 400 ЕДИНИЦ

        *//*for (PageModel pageModel : pageModelList) {
            indexRepository.deleteAllInBatch(indexRepository.findAllByPageModel(pageModel));
        }*//*

        for (LemmaModel lemmaModel : lemmaRepository.findAllBySiteModel(siteModel)) {

            if (indexingStop.isStopIndexingFlag()){
                lemmaRepository.deleteAllInBatch(lemmasForDeleting);
                lemmaRepository.saveAll(lemmasForUpdating);
                break;
            }

            if (lemmasForUpdating.contains(lemmaModel)){
                lemmaModel.get
            }

            // что делаем с леммой
            if (lemmaModel.getFrequency() == 1){
                lemmasForDeleting.add(lemmaModel);
            } else {
                lemmaModel.setFrequency(lemmaModel.getFrequency() - 1);
                lemmasForUpdating.add(lemmaModel);
            }

            // а что если в буфере содержится лемма, которую по факту сейчас опять же уменьшается?
            // и в итоге получаем, что несоответствие данные, т.е. лемма сохраняется 2 раза с неправильными данными... сука

            // удаление/обновление буфером
            if (lemmasForUpdating.size() == 400){
                lemmaRepository.deleteAllInBatch(lemmasForDeleting);
                lemmasForUpdating.clear();
            } else if (lemmasForDeleting.size() == 400) {
                lemmaRepository.saveAll(lemmasForUpdating);
                lemmasForDeleting.clear();
            }


        }

        lemmaRepository.deleteAllInBatch(lemmasForDeleting);
        lemmaRepository.saveAll(lemmasForUpdating);*/

    }

    public void clearTables(PageModel pageModel){

        indexRepository.deleteAllInBatch(indexRepository.findAllByPageModel(pageModel));
        lemmaRepository.deleteAllInBatch(lemmaRepository.findAllBySiteModel(pageModel.getSiteModel()));
        pageRepository.delete(pageModel);

    }


    public List<String> stopIndexing(){

        if (forkJoinPool == null || forkJoinPool.isShutdown()){
            return getResponse(true, "Индексация ещё не запущена");
        }
        indexingStop.setStopIndexingFlag(true);

        forkJoinPool.shutdownNow();

        return getResponse(false,"");
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

    public String[] getConnection(String path) throws IOException {

        Connection.Response connection = Jsoup.connect(path)
                .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                .referrer("http://www.google.com").timeout(10000).ignoreHttpErrors(true).execute();

        return new String[]{String.valueOf(connection.statusCode()),
                connection.parse().toString()};
    }














    public boolean getIndexingStatus(){
        return siteIndexProcessFlag || pageIndexProcessFlag;
    }

    public int getPagesCountOfSite(Site site){
        return pageRepository.countBySiteModel(siteRepository.findByUrl(site.getUrl()));
    }

    public int getLemmasCountOfSite(Site site){
        return lemmaRepository.countBySiteModel(siteRepository.findByUrl(site.getUrl()));
    }

    public String getSiteStatus(Site site) {
        // Если записи сайта ещё не существует
        if (siteRepository.findByUrl(site.getUrl()) == null){
            return Status.INDEXING.toString();
        }

        return siteRepository.findByUrl(site.getUrl()).getStatus().toString();
    }

    public long getSiteStatusTime(Site site){
        // Если записи сайта ещё не существует
        if (siteRepository.findByUrl(site.getUrl()) == null){
            return new Date().getTime();
        }

        return siteRepository.findByUrl(site.getUrl()).getStatusTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public String getSiteError(Site site){
        if (siteRepository.findByUrl(site.getUrl()) == null ||
                siteRepository.findByUrl(site.getUrl()).getLastError() == null){
            return "";
        }
        return siteRepository.findByUrl(site.getUrl()).getLastError();
    }

}