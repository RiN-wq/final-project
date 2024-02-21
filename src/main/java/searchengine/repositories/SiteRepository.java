package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.SiteModel;

@Repository
@Transactional
public interface SiteRepository extends JpaRepository<SiteModel, Integer> {
    SiteModel findByUrl(String url);
}
