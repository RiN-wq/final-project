package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
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
public class LemmaUtilImpl implements LemmaUtil {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ModelProcessingUtil modelProcessingUtil;

    List<String> russianOfficialPartsOfSpeech = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ");
    List<String> englishOfficialPartsOfSpeech = List.of("CONJ", "PREP", "PART", "INT");

    @Autowired
    public LemmaUtilImpl(LemmaRepository lemmaRepository,
                         IndexRepository indexRepository,
                         ModelProcessingUtil modelProcessingUtil) {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.modelProcessingUtil = modelProcessingUtil;
    }


    public List<String> splitTextIntoWords(String text) {
        text = text.replaceAll("[а-яА-Яa-zA-Z0-9._-]+@[а-яА-Яa-zA-Z0-9._-]+","");
        return List.of(text.replaceAll("[^а-яА-Яa-zA-Z-\\s]", "")
                .replaceAll("-"," ").toLowerCase().split("\\s+"));
    }

    public HashMap<String, Integer> makeLemmasMap(String text) {

        LuceneMorphology russianLuceneMorphology;
        LuceneMorphology englishLuceneMorphology;

        try {
            russianLuceneMorphology = new RussianLuceneMorphology();
            englishLuceneMorphology = new EnglishLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException();
        }

        HashMap<String, Integer> lemmasMap = new HashMap<>();

        for (String word : splitTextIntoWords(text)) {

            if (word.matches("[а-яА-Я]*?")) {
                lemmasMap = addLemmaToMap(word, lemmasMap, russianLuceneMorphology);
            } else if (word.matches("[a-zA-Z]*?")){
                lemmasMap = addLemmaToMap(word, lemmasMap, englishLuceneMorphology);
            }
        }
        return lemmasMap;

    }

    public HashMap<String, Integer> addLemmaToMap(String word,
                                                  HashMap<String, Integer> lemmasMap,
                                                  LuceneMorphology luceneMorphology) {

        if (word.isBlank() || isTheWordAnOfficialPartOfSpeech(word, luceneMorphology)) {
            return lemmasMap;
        }

        String lemma = removeTheSquareBrackets
                (String.valueOf(luceneMorphology.getNormalForms(word)));

        lemmasMap.put(lemma, (lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1));

        return lemmasMap;
    }

    public boolean isTheWordAnOfficialPartOfSpeech(String word, LuceneMorphology luceneMorphology) {

        String partOfTheSpeech = (luceneMorphology.getMorphInfo(removeTheSquareBrackets(word))
                .toString().split("\\s+")[1]);

        return russianOfficialPartsOfSpeech.contains(partOfTheSpeech) ||
                englishOfficialPartsOfSpeech.contains(partOfTheSpeech);

    }

    public String removeTheSquareBrackets(String text) {
        return text.replace("[", "").replace("]", "");
    }

    public String getAllTextFromHtml(String html) {
        return Jsoup.clean(html, Safelist.none()).toLowerCase();
    }

    public void addToLemmaAndIndexTables(String html,
                                         SiteModel siteModel,
                                         PageModel pageModel) {
        String htmlText = getAllTextFromHtml(html);
        HashMap<String, Integer> lemmasMap;

        lemmasMap = makeLemmasMap(htmlText);

        for (String lemma : lemmasMap.keySet()) {

            LemmaModel lemmaModel = modelProcessingUtil
                    .createOrUpdateLemmaModel(siteModel, lemma, new LemmaModel());

            modelProcessingUtil.saveIndexModel(pageModel, lemmaModel, lemmasMap.get(lemma));
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
