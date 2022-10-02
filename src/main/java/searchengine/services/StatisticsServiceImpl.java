package searchengine.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import searchengine.dto.statistics.StatisticsResponse;

import java.io.File;

@Service
public class StatisticsServiceImpl implements StatisticsService {
    @Override
    public StatisticsResponse getStatistics() {
        try {
            File file = ResourceUtils.getFile(
                "classpath:statistics.json"
            );
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(file, StatisticsResponse.class);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
