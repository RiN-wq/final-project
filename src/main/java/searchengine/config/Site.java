package searchengine.config;

import lombok.Data;

@Data
public class Site {
    private String url;
    private String name;

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }
}
