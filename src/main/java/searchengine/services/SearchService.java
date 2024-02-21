package searchengine.services;

import searchengine.dto.responses.SimpleResponse;
import searchengine.dto.searching.SearchData;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.responses.SearchResponse;
import searchengine.exceptions.EmptyRequestException;
import searchengine.exceptions.NoSearchResultException;
import searchengine.models.PageModel;

import java.util.List;
import java.util.Map;

public interface SearchService {
    SimpleResponse getSimpleErrorResponse(String errorText);

    SearchResponse getSearchResponse(SearchParameters searchParameters) throws
            EmptyRequestException, NoSearchResultException;

    Map<PageModel, Float> getRelativeRelevancePageModelMap(SearchParameters searchParameters)
            throws NoSearchResultException;

    List<PageModel> getRelevantPagesByRequest(String searchQuery, String siteUrl)
            throws NoSearchResultException;

    List<PageModel> getUpdatedBaseListOfPages(String siteUrl,
                                              String lemma,
                                              List<PageModel> baseListOfPages) throws NoSearchResultException;

    List<String> makeSortedByFrequencyLemmasList(String searchQuery);

    Map<PageModel, Float> calculateTheRelativePagesRelevance(List<PageModel> pageModelList);

    SearchData getSearchResponseData(PageModel pageModel,
                                     SearchParameters searchParameters,
                                     float relativeRelevance);

    String getPageTitle(PageModel pageModel);

    String getPageSnippet(PageModel pageModel, String lemma);
}
