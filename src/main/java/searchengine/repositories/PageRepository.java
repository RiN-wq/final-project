package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.PageModel;
import searchengine.models.SiteModel;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    List<PageModel> findAllBySiteModel(SiteModel siteModel);
    PageModel findByPath(String path);
    int countBySiteModel(SiteModel siteModel);

}
