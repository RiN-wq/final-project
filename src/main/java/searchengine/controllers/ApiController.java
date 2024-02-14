package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
    //TODO: Наладить систему возврата HTTP статусов (чтобы у всех не было HTTP.OK)

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @Autowired
    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
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
    public ResponseEntity<IndexingService> startPageIndexing(@RequestBody String path){
        System.out.println("ПОЕХАЛИ, НО 1 РАЗИК...!!!!!!!!!!");
        return new ResponseEntity(indexingService.indexPage(path), HttpStatus.OK);
    }

    @GetMapping(path = "/search")
    public ResponseEntity<IndexingService>  startSearching(@RequestBody SearchParameters searchParameters){
        System.out.println("Выводим, получается, список ответов по запросу...");
        return new ResponseEntity(searchService.getResponse(searchParameters), HttpStatus.OK);
    }

}
