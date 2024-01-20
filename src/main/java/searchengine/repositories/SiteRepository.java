package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteModel;

@Repository
public interface SiteRepository extends JpaRepository<SiteModel, Integer> {
    SiteModel findByUrl(String url);
}
