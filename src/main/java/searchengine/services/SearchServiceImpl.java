package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.responses.SimpleResponse;
import searchengine.dto.searching.SearchData;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.responses.SearchResponse;
import searchengine.exceptions.EmptyRequestException;
import searchengine.exceptions.NoSearchResultException;
import searchengine.models.IndexModel;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {
    private final int MAX_PAGE_FREQUENCY = 300;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaService lemmaService;
    private final SimpleResponse simpleResponse;
    private SearchResponse searchResponse;
    private SearchData searchData;

    public SearchServiceImpl(IndexRepository indexRepository,
                             SiteRepository siteRepository,
                             LemmaService lemmaService,
                             SimpleResponse simpleResponse,
                             SearchResponse searchResponse,
                             SearchData searchData) {
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        this.lemmaService = lemmaService;
        this.searchResponse = searchResponse;
        this.simpleResponse = simpleResponse;
        this.searchData = searchData;
    }

    public SimpleResponse getSimpleErrorResponse(String errorText) {

        simpleResponse.setResult(false);
        simpleResponse.setError(errorText);

        return simpleResponse;

    }

    public SearchResponse getSearchResponse(SearchParameters searchParameters) throws
            EmptyRequestException, NoSearchResultException {

        if (searchParameters.getQuery().isBlank()) {
            throw new EmptyRequestException("Ошибка пустого запроса");
        }

        Map<PageModel, Float> relevancePageModelMap = getRelativeRelevancePageModelMap(searchParameters);

        searchResponse = new SearchResponse();

        searchResponse.setResult(true);
        searchResponse.setCount(relevancePageModelMap.size());

        List<SearchData> searchDataList = new ArrayList<>();

        for (PageModel pageModel : relevancePageModelMap.keySet()) {
            searchDataList.add(getSearchResponseData(pageModel, searchParameters, relevancePageModelMap.get(pageModel)));
        }

        searchResponse.setData(searchDataList);

        return searchResponse;
    }

    public Map<PageModel, Float> getRelativeRelevancePageModelMap(SearchParameters searchParameters)
            throws NoSearchResultException {

        List<PageModel> pageModelList =
                getRelevantPagesByRequest(searchParameters.getQuery(), searchParameters.getSite());

        final Map<PageModel, Float> pagesRelativelyRelevanceMap = calculateTheRelativePagesRelevance(pageModelList);

        List<PageModel> sortedRelativelyRelevanceList = new ArrayList<>(pagesRelativelyRelevanceMap.keySet().stream()
                .sorted((pageOne, pageTwo) ->
                        Float.compare(pagesRelativelyRelevanceMap.get(pageOne), pagesRelativelyRelevanceMap.get(pageTwo)))
                .toList());

        Collections.reverse(sortedRelativelyRelevanceList);

        sortedRelativelyRelevanceList = sortedRelativelyRelevanceList.stream()
                .skip(searchParameters.getOffset())
                .limit(searchParameters.getLimit())
                .toList();

        Map<PageModel, Float> sortedPagesRelativelyRelevanceMap = new LinkedHashMap<>();

        for (PageModel page : sortedRelativelyRelevanceList) {
            sortedPagesRelativelyRelevanceMap.put(page, pagesRelativelyRelevanceMap.get(page));
        }

        return sortedPagesRelativelyRelevanceMap;
    }

    public List<PageModel> getRelevantPagesByRequest(String searchQuery, String siteUrl) throws NoSearchResultException {

        List<String> lemmasList = makeSortedByFrequencyLemmasList(searchQuery);

        List<PageModel> baseListOfPages = lemmaService.findAllPagesByLemma(lemmasList.get(0));

        if (baseListOfPages == null) {
            throw new NoSearchResultException("Ошибка пустого ответа");
        }

        for (String lemma : lemmasList) {

            baseListOfPages = getUpdatedBaseListOfPages(siteUrl, lemma, baseListOfPages);

        }
        return baseListOfPages;
    }

    public List<PageModel> getUpdatedBaseListOfPages(String siteUrl,
                                                     String lemma,
                                                     List<PageModel> baseListOfPages) throws NoSearchResultException {

        final List<PageModel> currentListOfPages;

        if (siteUrl != null) {
            currentListOfPages = lemmaService.findAllPagesByLemma(lemma).stream()
                    .filter(pageModel -> pageModel.getSiteModel().equals(siteRepository.findByUrl(siteUrl)))
                    .collect(Collectors.toList());
        } else {
            currentListOfPages = lemmaService.findAllPagesByLemma(lemma);
        }

        baseListOfPages = baseListOfPages.stream()
                .filter(currentListOfPages::contains).collect(Collectors.toList());

        if (baseListOfPages.isEmpty()) {
            throw new NoSearchResultException("Ошибка пустого ответа");
        }

        return baseListOfPages;
    }


    public List<String> makeSortedByFrequencyLemmasList(String searchQuery) {

        try {
            return lemmaService.makeLemmasMap(searchQuery).keySet().stream()
                    .filter(lemma -> (lemmaService.getFrequency(lemma) > 0)
                            && (lemmaService.getFrequency(lemma) < MAX_PAGE_FREQUENCY))
                    .sorted((keyOne, keyTwo) -> Integer.compare
                            (lemmaService.getFrequency(keyOne), lemmaService.getFrequency(keyTwo))).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Map<PageModel, Float> calculateTheRelativePagesRelevance(List<PageModel> pageModelList) {

        Map<PageModel, Float> pagesAbsolutelyRelevanceMap;

        pagesAbsolutelyRelevanceMap = pageModelList.stream()
                .collect(Collectors.toMap(pageModel -> pageModel,
                        pageModel -> indexRepository.findAllByPageModel(pageModel)
                                .stream().map(IndexModel::getRank)
                                .reduce(Float::sum).orElseThrow()));

        float maxAbsolutelyRelevance = pagesAbsolutelyRelevanceMap.values()
                .stream().max(Float::compare).orElseThrow();

        return pagesAbsolutelyRelevanceMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() / maxAbsolutelyRelevance));
    }

    public SearchData getSearchResponseData(PageModel pageModel,
                                            SearchParameters searchParameters,
                                            float relativeRelevance) {

        searchData = new SearchData();
        SiteModel siteModel = pageModel.getSiteModel();

        searchData.setSite(siteModel.getUrl());
        searchData.setSiteName(siteModel.getName());
        searchData.setUri(pageModel.getPath());
        searchData.setTitle(getPageTitle(pageModel));
        searchData.setSnippet(getPageSnippet(pageModel, searchParameters.getQuery()));
        searchData.setRelevance(relativeRelevance);

        return searchData;

    }

    public String getPageTitle(PageModel pageModel) {

        Pattern pattern = Pattern.compile("<title>(.*?)</title>");
        Matcher matcher = pattern.matcher(pageModel.getContent());

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "error";

    }

    public String getPageSnippet(PageModel pageModel, String searchQuery) {


        return "";
    }

}