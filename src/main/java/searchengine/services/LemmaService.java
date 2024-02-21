package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public interface LemmaService {
    String[] splitTextIntoWords(String text);

    HashMap<String, Integer> makeLemmasMap(String text) throws IOException;

    HashMap<String, Integer> addLemmaToMap(String word,
                                           HashMap<String, Integer> lemmasMap,
                                           LuceneMorphology luceneMorphology);

    boolean isTheWordAnOfficialPartOfSpeech(String word, LuceneMorphology luceneMorphology);

    String removeTheSquareBrackets(String text);

    String clearTags(String html);

    void addToLemmaAndIndexTables(String html, SiteModel siteWithIndexingPage, PageModel pageForIndexing);
    int getFrequency(String lemma);

    List<PageModel> findAllPagesByLemma(String lemma);

}
