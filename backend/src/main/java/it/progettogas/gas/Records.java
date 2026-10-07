package it.progettogas.gas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Records extends JpaRepository<RecordEntity, Long> {
  List<RecordEntity> findByKindOrderByIdDesc(String kind);
}
