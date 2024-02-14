package searchengine.services;

import searchengine.model.PageModel;
import searchengine.model.SiteModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public interface LemmaService {
    public String[] splitTextIntoWords(String text);
    public HashMap<String, Integer> makeLemmasMap(String text) throws IOException;
    public String clearTags(String html);
    public void addToLemmaAndIndexTables(String html, SiteModel siteWithIndexingPage, PageModel pageForIndexing);
    public int getFrequency(String lemma);
    public List<PageModel> findAllPagesByLemma(String lemma);

}
