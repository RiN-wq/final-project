package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.responses.SearchResponse;
import searchengine.dto.responses.SimpleResponse;
import searchengine.dto.searching.SearchParameters;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exceptions.EmptyRequestException;
import searchengine.exceptions.IndexingException;
import searchengine.exceptions.NoSearchResultException;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.List;

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
        return new ResponseEntity<>(statisticsService.getStatistics(), HttpStatus.FOUND);

    }

    @GetMapping(path = "/startIndexing")
    public ResponseEntity<List<SimpleResponse>> startIndexing() {
        return new ResponseEntity<>(indexingService.indexAllSites(), HttpStatus.CREATED);
    }

    @GetMapping(path = "/stopIndexing")
    public ResponseEntity<List<SimpleResponse>> stopIndexing() {
        return new ResponseEntity<>(indexingService.stopIndexing(), HttpStatus.OK);
    }

    @PostMapping(path = "/indexPage")
    public ResponseEntity<List<SimpleResponse>> indexPage(@RequestBody String path) {
        return new ResponseEntity<>(indexingService.indexPage(path), HttpStatus.CREATED);
    }

    @GetMapping(path = "/search")
    public ResponseEntity<SearchResponse> searchPagesByParameters(SearchParameters searchParameters) {
        try {
            return new ResponseEntity<>(searchService.getSearchResponse(searchParameters), HttpStatus.OK);
        } catch (EmptyRequestException e) {
            return new ResponseEntity(searchService.getSimpleErrorResponse("Задан пустой поисковый запрос"),
                    HttpStatus.BAD_REQUEST);
        } catch (NoSearchResultException e) {
            return new ResponseEntity(searchService.getSimpleErrorResponse(e.toString()),
                    HttpStatus.NOT_FOUND);
        } catch (IndexingException e) {
            return new ResponseEntity(searchService.getSimpleErrorResponse(e.toString()),
                    HttpStatus.NOT_FOUND);
        }
    }

}
