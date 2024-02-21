package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.models.IndexModel;
import searchengine.models.LemmaModel;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class LemmaServiceImpl implements LemmaService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ModelProcessingService modelProcessingService;

    List<String> officialPartsOfSpeech = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ");

    @Autowired
    public LemmaServiceImpl(LemmaRepository lemmaRepository,
                            IndexRepository indexRepository,
                            ModelProcessingService modelProcessingService) {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.modelProcessingService = modelProcessingService;
    }


    public String[] splitTextIntoWords(String text) {
        return text.replaceAll("[^а-яА-Я\\s]", "").split("\\s+");
    }

    public HashMap<String, Integer> makeLemmasMap(String text) {

        LuceneMorphology luceneMorphology;
        try {
            luceneMorphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        HashMap<String, Integer> lemmasMap = new HashMap<>();

        for (String word : splitTextIntoWords(text)) {

            lemmasMap = addLemmaToMap(word, lemmasMap, luceneMorphology);

        }
        return lemmasMap;

    }

    public HashMap<String, Integer> addLemmaToMap(String word,
                                                  HashMap<String, Integer> lemmasMap,
                                                  LuceneMorphology luceneMorphology) {

        if (word.isBlank() || isTheWordAnOfficialPartOfSpeech(word.toLowerCase(), luceneMorphology)) {
            return lemmasMap;
        }

        String lemma = removeTheSquareBrackets
                (String.valueOf(luceneMorphology.getNormalForms(word.toLowerCase())));

        lemmasMap.put(lemma, (lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1));

        return lemmasMap;
    }

    public boolean isTheWordAnOfficialPartOfSpeech(String word, LuceneMorphology luceneMorphology) {

        String partOfTheSpeech = (luceneMorphology.getMorphInfo(removeTheSquareBrackets(word))
                .toString().split("\\s+")[1]);

        return officialPartsOfSpeech.contains(partOfTheSpeech);

    }

    public String removeTheSquareBrackets(String text) {
        return text.replace("[", "").replace("]", "");
    }

    public String clearTags(String html) {
        return Jsoup.clean(html, Safelist.none());
    }

    public void addToLemmaAndIndexTables(String html,
                                         SiteModel siteModel,
                                         PageModel pageModel) {
        String htmlText = clearTags(html);
        HashMap<String, Integer> lemmasMap;

        lemmasMap = makeLemmasMap(htmlText);

        for (String lemma : lemmasMap.keySet()) {

            LemmaModel lemmaModel = modelProcessingService
                    .createOrUpdateLemmaModel(siteModel, lemma, new LemmaModel());

            modelProcessingService.saveIndexModel(pageModel, lemmaModel, lemmasMap.get(lemma));
        }
    }

    public int getFrequency(String lemma) {

        return (lemmaRepository.findByLemma(lemma) == null ?
                0 : lemmaRepository.findByLemma(lemma).getFrequency());

    }

    public List<PageModel> findAllPagesByLemma(String lemma) {

        List<PageModel> pageModelList = new ArrayList<>();

        List<IndexModel> indexModelList = indexRepository
                .findAllByLemmaModel(lemmaRepository.findByLemma(lemma));

        for (IndexModel modelOfIndex : indexModelList) {
            pageModelList.add(modelOfIndex.getPageModel());
        }

        return pageModelList;
    }
}
