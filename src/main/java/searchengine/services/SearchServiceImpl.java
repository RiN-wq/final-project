package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.searching.SearchResponse;
import searchengine.model.IndexModel;
import searchengine.model.PageModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService{

    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaService lemmaService;
    private final IndexingService indexingService;
    private SearchResponse searchResponse;

    public SearchServiceImpl(IndexRepository indexRepository,
                             SiteRepository siteRepository,
                             LemmaService lemmaService,
                             IndexingService indexingService){
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;

        this.lemmaService = lemmaService;
        this.indexingService = indexingService;

    }

    public List<SearchResponse> getResponse(SearchParameters searchParameters){

        List<SearchResponse> searchResponseList = new ArrayList<>();
        List<PageModel> pageModelList;

        try {
            pageModelList = searchParameters.getSite() != null ?
                            getRelevantPagesByRequest(searchParameters.getQuery(), searchParameters.getSite()) :
                            getRelevantPagesByRequest(searchParameters.getQuery());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (pageModelList.isEmpty()){
            return new ArrayList<>();
        }

        Map<PageModel, Float> pagesRelativelyRelevanceMap
                = calculatePageRelevance(pageModelList);

        pageModelList = pageModelList.stream()
                .sorted((pageOne, pageTwo) -> Float.compare(pagesRelativelyRelevanceMap.get(pageOne),
                        pagesRelativelyRelevanceMap.get(pageTwo)))
                .limit(searchParameters.getLimit()).collect(Collectors.toList());

        Collections.reverse(pageModelList);

        for (PageModel pageModel : pageModelList){

            searchResponse = new SearchResponse();

            searchResponse.setUrl(pageModel.getPath());
            searchResponse.setTitle(getPageTitle(pageModel));
            //searchResponse.setSnippet(getPageSnippet(pageModel, ));
            searchResponse.setRelevance(pagesRelativelyRelevanceMap.getOrDefault(pageModel, (float) 0));

            searchResponseList.add(searchResponse);

        }

        return searchResponseList;
    }

    public List<PageModel> getRelevantPagesByRequest(String searchQuery, String siteUrl) throws IOException{
        //TODO: ЗДЕСЬ ВЕЗДЕ СДЕЛАТЬ ПРОВЕРКИ НА NULL, ЧТОБЫ НЕ БЫЛО EXCEPTION, А СЕЙЧАС Я ЗАНЯТ ДРУГИМ!!!!!!!

        //TODO: Реазиловать  метод получения сниппета

        //TODO: Получение Limit настроил, осталось доделать offset (разобраться как с этим работать)
        List<PageModel> baseListOfPages = null;
        List<PageModel> currentListOfPages;

        List<String> lemmasList;

        lemmasList = makeSortedByFrequencyLemmaList(searchQuery);

        // сюда вставляю лемму с минимальным значением страниц
        baseListOfPages = lemmaService
                .findAllPagesByLemma(lemmasList.get(0));

        if (baseListOfPages == null){
            return new ArrayList<>();
        }

        for (String lemma : lemmasList) {

            currentListOfPages = lemmaService.findAllPagesByLemma(lemma);

            if (siteUrl != null){

                currentListOfPages = currentListOfPages.stream()
                        .filter(pageModel -> pageModel.getSiteModel().equals(siteRepository.findByUrl(siteUrl)))
                        .collect(Collectors.toList());

            }

            baseListOfPages = baseListOfPages.stream()
                    .filter(currentListOfPages::contains).collect(Collectors.toList());

            if (baseListOfPages.isEmpty()){
                return new ArrayList<>();
            }
        }
        return baseListOfPages;
    }

    public List<String> makeSortedByFrequencyLemmaList(String searchQuery){

        try {
            return lemmaService.makeLemmasMap(searchQuery).keySet().stream()
                    .filter(lemma -> {
                        return (lemmaService.getFrequency(lemma) > 0)
                                && (lemmaService.getFrequency(lemma) < 300);
                    })
                    .sorted((keyOne,keyTwo) -> Integer.compare
                            (lemmaService.getFrequency(keyOne), lemmaService.getFrequency(keyTwo))).toList();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<PageModel> getRelevantPagesByRequest(String searchQuery) throws IOException{

        return getRelevantPagesByRequest(searchQuery, null);

    }

    public Map<PageModel, Float> calculatePageRelevance(List<PageModel> pageModelList){

        float maxAbsolutelyRelevance;

        Map<PageModel, Float> pagesAbsolutelyRelevanceMap;

        // предлагаю сначала с помощью stream в map загнать: PageModel - значение абсолютной релеватности,
        // далее этот map пропустить опять же через stream, найти максимальное значение абсолютной релевантности
        // и вернуть другой map: PageModel - относительная релевантность

        pagesAbsolutelyRelevanceMap = pageModelList.stream()
                .collect(Collectors.toMap(pageModel -> pageModel, pageModel -> {
                            return indexRepository.findAllByPageModel(pageModel)
                                    .stream().map(IndexModel::getRank)
                                    .reduce(Float::sum).get();
                        }));

        maxAbsolutelyRelevance = pagesAbsolutelyRelevanceMap.values()
                .stream().max(Float::compare).get();

        return pagesAbsolutelyRelevanceMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()/maxAbsolutelyRelevance));
    }


    public String getPageTitle(PageModel pageModel) {

        Pattern pattern = Pattern.compile("<title>(.*?)</title>");
        Matcher matcher = pattern.matcher(pageModel.getContent());

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "error";
    }

    public String getPageSnippet(PageModel pageModel, String searchQuery){

        List<String> queryWordsList = makeSortedByFrequencyLemmaList(searchQuery);
        String htmlText = lemmaService.clearTags(pageModel.getContent())
                .replace("\n","").toLowerCase();
        Map<String, Integer> sentencesMap = new LinkedHashMap<>();
        Pattern pattern;
        Matcher matcher;

        // сначала проверяем все слова и составляем на каждое слово предложение
        // допустим будем выводить всего 3 предложения (типо это 3 строки)
        // сначала находим все предложения, далее проверяем, а вдруг предложения совпадают
        // мол 2 слова из запроса в 1ом предложении,в этом случае присваиваем предложению
        // 2 балла, и далее, сначала выводим по количеству баллов, а затем, если баллы совпадают,
        // то выводим уже по frequency предложения

        for (String word : queryWordsList) {

            pattern = Pattern.compile("\\.(.*?" + word + ".*?)\\.");
            matcher = pattern.matcher(word);

            while (matcher.find())
            {
               sentencesMap.put(matcher.group(1), 1);
            }

        }



        /*String regex = "([А-Яа-я0-9_]\s+){3}" + lemma + "([А-Яа-я0-9_]\s+){3}";

        return htmlText.replaceAll("^" + "(" + regex + ")", "");*/

        return "";
    }
}
