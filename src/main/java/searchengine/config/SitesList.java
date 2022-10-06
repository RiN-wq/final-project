package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties
public class SitesList {
    private List<Site> sites;
}
