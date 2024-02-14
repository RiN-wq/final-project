package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexModel, Integer> {
    List<IndexModel> findAllByPageModel(PageModel pageModel);
    List<IndexModel> findAllByLemmaModel(LemmaModel lemmaModel);
}
