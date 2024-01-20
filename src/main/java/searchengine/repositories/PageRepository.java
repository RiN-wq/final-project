package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    List<PageModel> findBySiteModel(SiteModel siteModel);
    PageModel findByPath(String path);
}
