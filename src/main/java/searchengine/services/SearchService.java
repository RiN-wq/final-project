package searchengine.services;

import searchengine.dto.searching.SearchParameters;
import searchengine.dto.searching.SearchResponse;
import searchengine.model.PageModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface SearchService {
    public List<SearchResponse> getResponse(SearchParameters searchParameters);
    public List<PageModel> getRelevantPagesByRequest(String searchQuery, String siteUrl) throws IOException;
    public List<String> makeSortedByFrequencyLemmaList(String searchQuery);
    public List<PageModel> getRelevantPagesByRequest(String searchQuery) throws IOException;
    public Map<PageModel, Float> calculatePageRelevance(List<PageModel> pageModelList);
    public String getPageTitle(PageModel pageModel);
    public String getPageSnippet(PageModel pageModel, String lemma);
}
