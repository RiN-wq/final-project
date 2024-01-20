package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @Autowired
    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping(path = "/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping(path = "/startIndexing")
    public ResponseEntity<IndexingService> startIndexing(){
        System.out.println("ПОЕХАЛИ...!!!!!!!!!!");
        return new ResponseEntity(indexingService.indexAllSites(), HttpStatus.OK);
    }

    @GetMapping(path = "/stopIndexing")
    public ResponseEntity<IndexingService>  stopIndexing(){
        System.out.println("ПРИЕХАЛИ....");
        return new ResponseEntity(indexingService.stopIndexing(), HttpStatus.OK);
    }

    @PostMapping(path = "/indexPage")
    public ResponseEntity<IndexingService> startPageIndexing(@RequestBody String url){
        System.out.println("ПОЕХАЛИ, НО 1 РАЗИК...!!!!!!!!!!");
        return new ResponseEntity(indexingService.indexPage(url), HttpStatus.OK);
    }

}
