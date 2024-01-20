package searchengine.services;

import java.io.IOException;
import java.util.HashMap;

public interface LemmaService {
    public String[] splitTextIntoWords(String text);
    public HashMap<String, Integer> makeLemmasMap(String text) throws IOException;
    public String clearTags(String html);

}
