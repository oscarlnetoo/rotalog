package com.rotalog.repository;

import com.rotalog.domain.Manutencao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ManutencaoRepository
 * 
 * FIXME: Sem paginação
 * FIXME: Sem ordenação padrão
 */
@Repository
public interface ManutencaoRepository extends JpaRepository<Manutencao, Long> {

    List<Manutencao> findByVeiculoId(Long veiculoId);

    List<Manutencao> findByStatus(String status);

    List<Manutencao> findByVeiculoIdAndStatus(Long veiculoId, String status);

    // FIXME: Query nativa quando poderia ser JPQL
    @Query(value = "SELECT * FROM manutencoes WHERE veiculo_id = :veiculoId ORDER BY data_manutencao DESC LIMIT 1", nativeQuery = true)
    Manutencao findUltimaManutencao(@Param("veiculoId") Long veiculoId);

    // Última manutenção CONCLUIDA (qualquer tipo); data_manutencao pode ser NULL, então cai para data_atualizacao
    @Query(value = "SELECT * FROM manutencoes WHERE veiculo_id = :veiculoId AND status = 'CONCLUIDA' ORDER BY COALESCE(data_manutencao, data_atualizacao) DESC LIMIT 1", nativeQuery = true)
    Manutencao findUltimaManutencaoConcluida(@Param("veiculoId") Long veiculoId);

    // TODO: Adicionar query para manutenções pendentes por período
    // TODO: Adicionar paginação
}
