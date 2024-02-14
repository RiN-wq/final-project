package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.services.LemmaServiceImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) throws IOException {

     String mydata = "Всем привет. Меня зовут Данила. Я учусь на программиста.";

     Pattern pattern = Pattern.compile("\\.(.*?меня.*?)\\.");
     Matcher matcher = pattern.matcher(mydata);
     while (matcher.find())
     {
            System.out.println(matcher.group(1));
     }

    }

//TODO: ПОЧИТАТЬ ПРО PATTERN И MATCHER КЛАССЫ!!!
}
