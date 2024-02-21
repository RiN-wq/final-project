package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.IndexModel;
import searchengine.models.LemmaModel;
import searchengine.models.PageModel;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexModel, Integer> {
    List<IndexModel> findAllByPageModel(PageModel pageModel);
    List<IndexModel> findAllByLemmaModel(LemmaModel lemmaModel);
}
