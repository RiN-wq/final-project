package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;

import java.util.List;

@Repository
@Transactional
public interface PageRepository extends JpaRepository<PageModel, Integer> {
    List<PageModel> findAllBySiteModel(SiteModel siteModel);
    PageModel findByPath(String path);
    int countBySiteModel(SiteModel siteModel);

}
