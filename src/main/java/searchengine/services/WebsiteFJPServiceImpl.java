package searchengine.services;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingStop;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.RecursiveAction;

//TODO: Multi Insert реализовать
@Service
public class WebsiteFJPServiceImpl extends RecursiveAction
        implements WebsiteFJPService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteModel siteModel;
    private PageModel pageModel;

    String regexFiles = "^.*\\.(jpg|JPG|gif|GIF|doc|DOC|pdf|PDF)$";
    String regexWebsite;
    String path; // путь страницы
    int statusCode;


    private final LemmaService lemmaService;
    private final IndexingStop indexingStop;


    @Autowired
    public WebsiteFJPServiceImpl(SiteRepository siteRepository,
                                 PageRepository pageRepository,
                                 SiteModel siteModel,
                                 PageModel pageModel,
                                 LemmaService lemmaService,
                                 IndexingStop indexingStop){
        this.pageModel = pageModel;
        this.siteRepository = siteRepository;
        this.siteModel = siteModel;
        this.pageRepository = pageRepository;
        this.lemmaService = lemmaService;
        this.indexingStop = indexingStop;
        regexWebsite = siteModel.getUrl() + "[^#]*";
    }
    @Override
    public void compute(){

        if (indexingStop.isStopIndexingFlag()){
            Thread.currentThread().interrupt();
        }

        Document webPage = null;

        pageModel.setSiteModel(siteModel);

        try {

            Thread.sleep(150);

            Connection.Response response = Jsoup.connect(pageModel.getPath())
                    .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                    .referrer("http://www.google.com").timeout(10000).ignoreHttpErrors(true).execute();

            webPage = response.parse();

            statusCode = response.statusCode();

            if (statusCode != 200){
                throw new IOException();
            }

            pageModel.setCode(statusCode);
            pageModel.setContent(webPage.toString());


            //TODO: ВОТ ТЕПЕРЬ СИНХРОНИЗИРОВАННО И ДУБЛИКАТОВ НЕМА!
            synchronized (pageRepository){
                // если уже добавлена страница
                if (pageRepository.findByPath(pageModel.getPath()) != null){
                    return;
                }
                pageRepository.save(pageModel);
            }

            siteModel.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteModel);


        } catch (IOException | InterruptedException e) {
            if (siteModel.getUrl().equals(pageModel.getPath())){
                siteModel.setLastError("Сайт не существует");
                siteModel.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteModel);
                return;
            }

            return;
        }
        lemmaService.addToLemmaAndIndexTables(webPage.toString(), siteModel, pageModel);

        Elements links = webPage.select("a");


        for (Element link : links) {

            if (indexingStop.isStopIndexingFlag()){
                Thread.currentThread().interrupt();
            }

            path = link.absUrl("href");

            if (!(path.matches(regexWebsite)) ||
                    (path.matches(regexFiles))) {
                continue;
            }

            pageModel = new PageModel();
            pageModel.setPath(path);

            WebsiteFJPServiceImpl recursiveWebsiteMap =
                    new WebsiteFJPServiceImpl(siteRepository, pageRepository, siteModel, pageModel, lemmaService, indexingStop); // рекурсивный вызов класса

            recursiveWebsiteMap.fork(); // отправление задачи в очередь ПОТОКА (НО НЕ ЗАПУСК ВЫПОЛНЕНИЯ)
        }
    }
}