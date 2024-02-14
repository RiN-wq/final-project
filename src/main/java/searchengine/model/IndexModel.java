package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "index_")
@Component
public class IndexModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne(cascade = CascadeType.MERGE/*, fetch = FetchType.LAZY*/)
    @JoinColumn(name = "page_id", nullable = false)
    private PageModel pageModel;
    @ManyToOne(cascade = CascadeType.MERGE/*, fetch = FetchType.LAZY*/)
    @JoinColumn(name = "lemma_id", nullable = false)
    private LemmaModel lemmaModel;
    @Column(name = "rank_",columnDefinition = "FLOAT NOT NULL")
    private float rank;
}
