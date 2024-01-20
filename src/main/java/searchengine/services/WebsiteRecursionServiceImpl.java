package searchengine.services;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.RecursiveAction;

//TODO: Multi Insert реализовать
//TODO: Решить проблему с добавлением дубликатов ключей (это как раз и решит проблему перескакивания id в таблице)
@Service
public class WebsiteRecursionServiceImpl extends RecursiveAction
        implements WebsiteRecursionService{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SiteModel siteModel;
    private PageModel pageModel;

    String regexFiles = "^.*\\.(jpg|JPG|gif|GIF|doc|DOC|pdf|PDF)$";
    String regexWebsite;
    String url;
    String path; // путь страницы
    int statusCode;

    @Autowired
    public WebsiteRecursionServiceImpl(SiteRepository siteRepository,
                                       PageRepository pageRepository,
                                       SiteModel siteModel,
                                       PageModel pageModel){
        this.pageModel = pageModel;
        this.siteRepository = siteRepository;
        this.siteModel = siteModel;
        this.pageRepository = pageRepository;
    }
    @Override
    public void compute(){

        url = pageModel.getPath();

        regexWebsite = siteModel.getUrl() + "[^#]*";

        Document webPage = null;
        Connection.Response response;

        pageModel.setSiteModel(siteModel);

        try {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0")
                    .referrer("http://www.google.com").timeout(10000).ignoreHttpErrors(true).execute();

            webPage = response.parse();

            statusCode = response.statusCode();
            pageModel.setCode(statusCode);
            pageModel.setContent(statusCode == 404 ? "" : webPage.toString());
            pageRepository.save(pageModel);

            siteModel.setStatusTime(LocalDateTime.now());

            if(statusCode == 503 && siteModel.getUrl().equals(pageModel.getPath())){
                siteModel.setLastError("Сервер не может обработать страницу в данный момент");
                siteRepository.save(siteModel);
                return;
            }
            siteRepository.save(siteModel);


        } catch (IOException e) {
            if (siteModel.getUrl().equals(pageModel.getPath())){
                siteModel.setLastError("Сайт не существует");
                siteModel.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteModel);
                return;
            }
            //415 ошибка - некорректный тип данных
            pageModel.setCode(415);
            pageModel.setContent("");
            pageRepository.save(pageModel);
            return;
        }

        if (statusCode == 404){
            return;
        }

        Elements links = webPage.select("a");

        for (Element link : links) {
            path = link.absUrl("href");

            if (pageRepository.findByPath(path) != null ||
                    !(path.matches(regexWebsite)) ||
                    (path.matches(regexFiles))) {
                continue;
            }

            pageModel = new PageModel();
            pageModel.setPath(path);

            WebsiteRecursionServiceImpl recursiveWebsiteMap =
                    new WebsiteRecursionServiceImpl(siteRepository, pageRepository, siteModel, pageModel); // рекурсивный вызов класса

            recursiveWebsiteMap.fork(); // отправление задачи в очередь ПОТОКА (НО НЕ ЗАПУСК ВЫПОЛНЕНИЯ)
        }
    }
}