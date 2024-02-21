package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.LemmaModel;
import searchengine.models.SiteModel;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaModel, Integer> {
    LemmaModel findByLemma(String lemma);
    int countBySiteModel(SiteModel siteModel);
    List<LemmaModel> findAllBySiteModel(SiteModel siteModel);
}
