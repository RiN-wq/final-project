package searchengine.utils;

import org.jsoup.select.Elements;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;

import java.util.Map;

public interface WebsiteFJPService {
    Map<PageModel, Elements> setAllModelsForPageModel(SiteModel siteModel, PageModel pageModel);
    void addPathsToTaskPool(Elements links);
}
