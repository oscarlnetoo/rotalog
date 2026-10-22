package com.rotalog.repository;

import com.rotalog.domain.AlertaManutencao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface AlertaManutencaoRepository extends JpaRepository<AlertaManutencao, Long> {

    List<AlertaManutencao> findAllByOrderByDataCriacaoDesc();

    List<AlertaManutencao> findByStatusOrderByDataCriacaoDesc(String status);

    // Alertas abertos (PENDENTE/ENVIADO) criados depois da data base - checagem de duplicidade
    boolean existsByVeiculoIdAndStatusInAndDataCriacaoAfter(Long veiculoId, Collection<String> status, LocalDateTime depoisDe);

    List<AlertaManutencao> findByStatusAndTentativasLessThanOrderByDataCriacaoAsc(String status, Integer tentativas);

    List<AlertaManutencao> findByStatusAndTentativasGreaterThanEqual(String status, Integer tentativas);
}
