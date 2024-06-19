package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import searchengine.dto.responses.SimpleErrorResponse;
import searchengine.dto.searching.SearchData;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.responses.SearchResponse;
import searchengine.exceptions.EmptyRequestException;
import searchengine.exceptions.IndexingException;
import searchengine.exceptions.NoSearchResultException;
import searchengine.models.PageModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface SearchService {
    SimpleErrorResponse getSimpleErrorResponse(String errorText);

    SearchResponse getSearchResponse(SearchParameters searchParameters) throws
            EmptyRequestException, NoSearchResultException, IndexingException;

    boolean isListOfSitesWasIndexed(SearchParameters searchParameters);

    Map<PageModel, Float> getSortedRelativeRelevancePageModelMap(SearchParameters searchParameters)
            throws NoSearchResultException;

    Map<PageModel, Float> applyLimitAndOffsetForResults(Map<PageModel, Float> relevancePageModelMap,
                                                        SearchParameters searchParameters) throws NoSearchResultException;

    List<PageModel> getRelevantPagesByRequest(String searchQuery, String siteUrl) throws NoSearchResultException;

    List<PageModel> getUpdatedBaseListOfPages(String siteUrl,
                                              String lemma,
                                              List<PageModel> baseListOfPages) throws NoSearchResultException;

    List<String> makeSortedByFrequencyLemmasList(String searchQuery);

    Map<PageModel, Float> getTheRelativePagesRelevanceMap(List<PageModel> pageModelList);

    SearchData getSearchResponseData(PageModel pageModel,
                                     SearchParameters searchParameters,
                                     float relativeRelevance);

    String getPageTitle(PageModel pageModel);

    String getPageSnippet(PageModel pageModel, String searchQuery) throws IOException;

    String sortMapAndGetSentences(Map<String, List<String>> keywordsSentencesMap);

    String highlightKeywordsInSentence(String sentence,
                                       List<String> keywordsList);

    String getNormalFormOfWord(LuceneMorphology russianLuceneMorphology,
                               LuceneMorphology englishLuceneMorphology,
                               String word);

    List<String> getSentencesListFromText(String text, String word);

    Map<String, List<String>> getMapOfSentences(String word,
                                                List<String> sentencesList,
                                                Map<String, List<String>> keywordsSentencesMap);

}
