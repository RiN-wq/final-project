package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class DefaultController {

    /**
     * Метод формирует страницу из HTML файла index.html
     * которые находится в resources/templates
     * Это делает библиотека Thymeleaf
     */
    @RequestMapping("/")
    public String index() {
        return "index";
    }
}
