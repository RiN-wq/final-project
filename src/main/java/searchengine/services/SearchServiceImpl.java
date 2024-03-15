package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.dto.responses.SimpleResponse;
import searchengine.dto.searching.SearchData;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.responses.SearchResponse;
import searchengine.exceptions.EmptyRequestException;
import searchengine.exceptions.IndexingException;
import searchengine.exceptions.NoSearchResultException;
import searchengine.models.IndexModel;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;
import searchengine.models.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaUtil;

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
    private final LemmaUtil lemmaUtil;
    private final SimpleResponse simpleResponse;
    private final SearchResponse searchResponse;
    private SearchData searchData;

    public SearchServiceImpl(IndexRepository indexRepository,
                             SiteRepository siteRepository,
                             LemmaUtil lemmaUtil,
                             SimpleResponse simpleResponse,
                             SearchResponse searchResponse,
                             SearchData searchData) {
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
        this.lemmaUtil = lemmaUtil;
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
            EmptyRequestException, NoSearchResultException, IndexingException {

        if (searchParameters.getQuery().isBlank()) {
            throw new EmptyRequestException("Ошибка пустого запроса");
        }

        /*if (!isListOfSitesWasIndexed(searchParameters)) {
            throw new IndexingException("Требуемые сайты частично или полностью не проиндексированы");
        }*/

        Map<PageModel, Float> relevancePageModelMap = getSortedRelativeRelevancePageModelMap(searchParameters);

        searchResponse.setResult(true);
        searchResponse.setCount(relevancePageModelMap.size());

        relevancePageModelMap = applyLimitAndOffsetForResults(relevancePageModelMap, searchParameters);

        List<SearchData> searchDataList = new ArrayList<>();

        for (PageModel pageModel : relevancePageModelMap.keySet()) {
            searchDataList.add(getSearchResponseData(pageModel, searchParameters, relevancePageModelMap.get(pageModel)));
        }

        searchResponse.setData(searchDataList);

        return searchResponse;
    }

    public boolean isListOfSitesWasIndexed(SearchParameters searchParameters) {

        if (searchParameters.getSite() != null &&
                siteRepository.findByUrl(searchParameters.getSite()).getStatus() == Status.INDEXED) {
            return true;
        } else if (searchParameters.getSite() != null) {
            return false;
        }

        for (SiteModel siteModel : siteRepository.findAll()) {

            if (siteModel.getStatus() != Status.INDEXED) {
                return false;
            }

        }
        return true;

    }

    public Map<PageModel, Float> getSortedRelativeRelevancePageModelMap(SearchParameters searchParameters)
            throws NoSearchResultException {

        List<PageModel> pageModelList =
                getRelevantPagesByRequest(searchParameters.getQuery(), searchParameters.getSite());

        final Map<PageModel, Float> pagesRelativelyRelevanceMap = getTheRelativePagesRelevanceMap(pageModelList);

        List<PageModel> sortedRelativelyRelevanceList = new ArrayList<>(pagesRelativelyRelevanceMap.keySet().stream()
                .sorted((pageOne, pageTwo) ->
                        Float.compare(pagesRelativelyRelevanceMap.get(pageOne), pagesRelativelyRelevanceMap.get(pageTwo)))
                .toList());

        Collections.reverse(sortedRelativelyRelevanceList);

        Map<PageModel, Float> sortedPagesRelativelyRelevanceMap = new LinkedHashMap<>();

        for (PageModel page : sortedRelativelyRelevanceList) {
            sortedPagesRelativelyRelevanceMap.put(page, pagesRelativelyRelevanceMap.get(page));
        }

        return sortedPagesRelativelyRelevanceMap;

    }

    public Map<PageModel, Float> applyLimitAndOffsetForResults(Map<PageModel, Float> relevancePageModelMap,
                                                               SearchParameters searchParameters)
            throws NoSearchResultException {

        relevancePageModelMap = relevancePageModelMap.keySet().stream()
                .skip(searchParameters.getOffset())
                .limit(searchParameters.getLimit())
                .collect(Collectors.toMap(page -> page, relevancePageModelMap::get, (o, n) -> n, LinkedHashMap::new));

        if (relevancePageModelMap.isEmpty()) {
            throw new NoSearchResultException("Ошибка пустого вывода");
        }

        return relevancePageModelMap;
    }

    public List<PageModel> getRelevantPagesByRequest(String searchQuery, String siteUrl)
            throws NoSearchResultException {

        List<String> lemmasList = makeSortedByFrequencyLemmasList(searchQuery);

        List<PageModel> baseListOfPages = lemmaUtil.findAllPagesByLemma(lemmasList.get(0));

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
            currentListOfPages = lemmaUtil.findAllPagesByLemma(lemma).stream()
                    .filter(pageModel -> pageModel.getSiteModel().equals(siteRepository.findByUrl(siteUrl)))
                    .collect(Collectors.toList());
        } else {
            currentListOfPages = lemmaUtil.findAllPagesByLemma(lemma);
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
            return lemmaUtil.makeLemmasMap(searchQuery).keySet().stream()
                    .filter(lemma -> (lemmaUtil.getFrequency(lemma) > 0)
                            && (lemmaUtil.getFrequency(lemma) < MAX_PAGE_FREQUENCY))
                    .sorted((keyOne, keyTwo) -> Integer.compare
                            (lemmaUtil.getFrequency(keyOne), lemmaUtil.getFrequency(keyTwo))).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public Map<PageModel, Float> getTheRelativePagesRelevanceMap(List<PageModel> pageModelList) {

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
        searchData.setUri(pageModel.getPath().replaceAll(pageModel.getSiteModel().getUrl(), ""));
        searchData.setTitle(getPageTitle(pageModel));
        try {
            searchData.setSnippet(getPageSnippet(pageModel, searchParameters.getQuery()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public String getPageSnippet(PageModel pageModel, String searchQuery) throws IOException {

        List<String> searchQueryList = lemmaUtil.makeLemmasMap(searchQuery).keySet().stream().toList();
        LuceneMorphology russianLuceneMorphology = new RussianLuceneMorphology();
        LuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();

        String text = lemmaUtil.getAllTextFromHtml(pageModel.getContent()
                .replaceAll("((<a[\\S\\s]*?>)[\\S\\s]*?(</a>))", ""));

        List<String> wordsOfText = lemmaUtil.splitTextIntoWords(text);

        Map<String, List<String>> keywordsSentencesMap = new LinkedHashMap<>();

        for (String word : wordsOfText) {

            if (word.isBlank()) {
                continue;
            }

            String normalFormOfWord = getNormalFormOfWord
                    (russianLuceneMorphology, englishLuceneMorphology, word);

            if (searchQueryList.contains(normalFormOfWord)) {
                keywordsSentencesMap =
                        getMapOfSentences(word, getSentencesListFromText(text, word), keywordsSentencesMap);
            }
        }
        return sortMapAndGetSentences(keywordsSentencesMap);
    }

    public String sortMapAndGetSentences(Map<String, List<String>> keywordsSentencesMap) {
        List<String> snippetSentencesList = keywordsSentencesMap.keySet().stream()
                .sorted(Comparator.comparingInt(sentence -> keywordsSentencesMap.get(sentence).size()))
                .limit(2)
                .map(sentence -> highlightKeywordsInSentence(sentence, keywordsSentencesMap.get(sentence)))
                .toList();

        StringBuilder snippet = new StringBuilder();

        for (String snippetSentence : snippetSentencesList) {
            snippet.append(snippetSentence);
        }

        return snippet.toString();
    }

    public String highlightKeywordsInSentence(String sentence,
                                              List<String> keywordsList) {

        for (String word : keywordsList) {
            sentence = sentence.replaceAll
                    (word, "<b>" + word + "<\\/b>");
        }

        return sentence;

    }


    public String getNormalFormOfWord(LuceneMorphology russianLuceneMorphology,
                                      LuceneMorphology englishLuceneMorphology,
                                      String word) {

        if (word.matches("[а-яА-Я]*?")) {
            return lemmaUtil.removeTheSquareBrackets
                    (String.valueOf(russianLuceneMorphology.getNormalForms(word.toLowerCase())));
        } else if (word.matches("[a-zA-Z]*?")) {
            return lemmaUtil.removeTheSquareBrackets
                    (String.valueOf(englishLuceneMorphology.getNormalForms(word.toLowerCase())));
        }

        return null;

    }

    public List<String> getSentencesListFromText(String text, String word) {

        String regex = "([.?!;\n][^.?!;\n]*?(" + word + ".*?)[.?!;\n])";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        List<String> sentencesList = new ArrayList<>();
        int start = 0;
        while (matcher.find(start)) {
            start = matcher.end(1);
            sentencesList.add(matcher.group().substring(1));
        }

        sentencesList = sentencesList.stream().filter(sentence -> sentence.length() < 195).toList();

        return sentencesList;

    }

    public Map<String, List<String>> getMapOfSentences(String word,
                                                       List<String> sentencesList,
                                                       Map<String, List<String>> keywordsSentencesMap) {

        List<String> keywordsOfSentence;

        for (String sentence : sentencesList) {

            keywordsOfSentence = new ArrayList<>();

            if (keywordsSentencesMap.containsKey(sentence)) {
                keywordsOfSentence = keywordsSentencesMap.get(sentence);
            }

            if (keywordsOfSentence.contains(word)) {
                continue;
            }

            keywordsOfSentence.add(word);

            keywordsSentencesMap.put(sentence, keywordsOfSentence);

        }

        return keywordsSentencesMap;

    }

}