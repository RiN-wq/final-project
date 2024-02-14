package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.IndexingStop;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Service
public class LemmaServiceImpl implements LemmaService{

    HashMap<String, Integer> lemmasMap;
    private LemmaModel lemmaModel;
    private IndexModel indexModel;

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingStop indexingStop;

    List<String> officialPartsOfSpeech = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ");

    @Autowired
    public LemmaServiceImpl(LemmaModel lemmaModel,
                            IndexModel indexModel,
                            LemmaRepository lemmaRepository,
                            IndexRepository indexRepository,
                            IndexingStop indexingStop){
        this.lemmaModel = lemmaModel;
        this.indexModel = indexModel;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.indexingStop = indexingStop;
    }


    public String[] splitTextIntoWords(String text) {
        return text.replaceAll("[^а-яА-Я\\s]", "").split("\\s+");
    }

    public HashMap<String, Integer> makeLemmasMap(String text) throws IOException {
        String lemma;

        String[] words = splitTextIntoWords(text);

        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        lemmasMap = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()){
                continue;
            }
            word = word.toLowerCase();

            String partOfTheSpeech = (luceneMorphology.getMorphInfo(word).toString()
                    .replace("[","").replace("]",""))
                    .split("\\s+")[1];

            if (officialPartsOfSpeech.contains(partOfTheSpeech)){
                continue;
            }

            lemma = String.valueOf(luceneMorphology.getNormalForms(word));
            if (lemma.isBlank()){
                continue;
            }
            lemma = lemma.replace("[","").replace("]","");

            lemmasMap.put(lemma, (lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1));
        }
        return lemmasMap;
    }

    //TODO: MAKE METHOD FOR REPLACING BRACKETS ("[]")

    public String clearTags(String html){
        return Jsoup.clean(html, Safelist.none());
    }

    public void addToLemmaAndIndexTables(String html,
                                         SiteModel siteModel,
                                         PageModel pageModel) {
        //TODO: А делать что если наш лемматизатор возвращает 2 варианта леммы для конкретного слова?
        //TODO: М.б. в лемматизатор можно запихнуть сразу текст и тогда отпадёт вариант с таким возвратом?
        //TODO: И нужный вариант леммы будет выбираться из контекста???
        String htmlText = clearTags(html);
        HashMap<String, Integer> lemmasMap;
        try {
            lemmasMap = makeLemmasMap(htmlText);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



        for (String lemma : lemmasMap.keySet()) {

            if (indexingStop.isStopIndexingFlag()){
                break;
            }

            lemmaModel = new LemmaModel();

            lemmaModel.setSiteModel(siteModel);
            lemmaModel.setLemma(lemma);

            lemmaModel = synchronizedAddFrequency(lemmaModel, lemma);

            indexModel = new IndexModel();

            indexModel.setLemmaModel(lemmaModel);
            indexModel.setPageModel(pageModel);
            indexModel.setRank(lemmasMap.get(lemma));

            indexRepository.save(indexModel);
        }
    }

    synchronized public LemmaModel synchronizedAddFrequency(LemmaModel modelOfLemma, String lemma){

        if (lemmaRepository.findByLemma(lemma) != null){
            modelOfLemma = lemmaRepository.findByLemma(lemma);
            modelOfLemma.setFrequency(modelOfLemma.getFrequency() + 1);
        } else {
            modelOfLemma.setFrequency(1);
        }

        lemmaRepository.save(modelOfLemma);
        return modelOfLemma;
    }

    //TODO: Экспериментальным путём выяснил, что в некоторых случаях добавляется много записей в IndexTable по всего
    // лишь 1ой лемме, но при этом frequency у леммы = 1. КАК ТАКОЕ ВОЗМОЖНО?!?!

    public int getFrequency(String lemma){

        return (lemmaRepository.findByLemma(lemma) == null ?
                0 : lemmaRepository.findByLemma(lemma).getFrequency());

    }

    //TODO: Сделать так, чтобы стек вызовов методов был минимальный, т.е. методы вызывались бы по очереди, а не 1 в другом!!!
    public List<PageModel> findAllPagesByLemma(String lemma){

        List<PageModel> pageModelList = new ArrayList<>();

        List<IndexModel> indexModelList = indexRepository
                .findAllByLemmaModel(lemmaRepository.findByLemma(lemma));

        for (IndexModel modelOfIndex : indexModelList) {
            pageModelList.add(modelOfIndex.getPageModel());
        }

        return pageModelList;
    }
}
