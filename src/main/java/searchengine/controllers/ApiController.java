package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exceptions.EmptyRequestException;
import searchengine.exceptions.NoSearchResultException;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
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
        return new ResponseEntity(statisticsService.getStatistics(), HttpStatus.FOUND);

    }

    @GetMapping(path = "/startIndexing")
    public ResponseEntity<IndexingService> startIndexing() {
        try {
            return new ResponseEntity(indexingService.indexAllSites(), HttpStatus.CREATED);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping(path = "/stopIndexing")
    public ResponseEntity<IndexingService> stopIndexing() {
        return new ResponseEntity(indexingService.stopIndexing(), HttpStatus.OK);
    }

    @PostMapping(path = "/indexPage")
    public ResponseEntity<IndexingService> startPageIndexing(@RequestBody String path) {
        return new ResponseEntity(indexingService.indexPage(path), HttpStatus.CREATED);
    }

    @GetMapping(path = "/search")
    public ResponseEntity<IndexingService> startSearching(@RequestBody SearchParameters searchParameters) {
        try {
            return new ResponseEntity(searchService.getSearchResponse(searchParameters), HttpStatus.FOUND);
        } catch (EmptyRequestException e) {
            return new ResponseEntity(searchService.getSimpleErrorResponse("Задан пустой поисковый запрос"),
                    HttpStatus.BAD_REQUEST);
        } catch (NoSearchResultException e) {
            return new ResponseEntity(searchService.getSimpleErrorResponse("Ничего не найдено"),
                    HttpStatus.NOT_FOUND);
        }
    }

}
