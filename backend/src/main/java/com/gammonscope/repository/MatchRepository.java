package com.gammonscope.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.gammonscope.dto.MatchSummary;
import com.gammonscope.model.BgMatch;

public interface MatchRepository extends JpaRepository<BgMatch, Long> {

    @Query("select new com.gammonscope.dto.MatchSummary("
            + "m.id, m.player0, m.player1, m.matchLength, m.variation, m.result, "
            + "m.sourceFormat, m.event, m.matchDate, m.importedAt, size(m.games)) "
            + "from BgMatch m order by m.importedAt desc")
    List<MatchSummary> findAllSummaries();
}
