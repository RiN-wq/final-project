package searchengine;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {

     String myData = "Всем привет. Меня зовут Данила. Я учусь на программиста.";

     Pattern pattern = Pattern.compile("\\.(.*?меня.*?)\\.");
     Matcher matcher = pattern.matcher(myData);
     while (matcher.find())
     {
            System.out.println(matcher.group(1));
     }

    }
}
