package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;


@Service
public class LemmaServiceImpl implements LemmaService{

    HashMap<String, Integer> lemmasMap;
    List<String> officialPartsOfSpeech = List.of("МЕЖД", "ПРЕДЛ", "СОЮЗ");

    @Autowired
    public LemmaServiceImpl(){

    }


    public String[] splitTextIntoWords(String text) {
        String clearText = text.replaceAll("[^а-яА-Я' ']", "");
        String[] words = clearText.split("\\s+");
        return words;
    }

    public HashMap<String, Integer> makeLemmasMap(String text) throws IOException {
        String lemma;
        int numberOfLemma;

        String[] words = splitTextIntoWords(text);

        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        lemmasMap = new HashMap<>();

        for (String word : words) {
            String partOfTheSpeech = String.valueOf(luceneMorphology.getMorphInfo(word))
                    .split("\\s+")[1];

            if (officialPartsOfSpeech.contains(partOfTheSpeech) || word.isBlank()){
                continue;
            }

            lemma = String.valueOf(luceneMorphology.getNormalForms(word));
            if (lemma.isBlank()){
                continue;
            }

            if (lemmasMap.containsKey(lemma)){
                numberOfLemma = lemmasMap.get(lemma);
                lemmasMap.put(lemma, ++numberOfLemma);

            }
        }
        System.out.println(lemmasMap);
        return lemmasMap;
    }

    public String clearTags(String html){
        return Jsoup.clean(html, Safelist.none());

    }
}
