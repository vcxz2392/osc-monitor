package com.osc.monitor.revision.repository;

import java.util.Optional;

import com.osc.monitor.revision.repository.entity.RevisionSeq;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevisionRepository extends JpaRepository<RevisionSeq, Integer> {

    /**
     * 리비전 발급. 어긋나면 클라이언트가 변경을 통째로 놓치거나 중복으로 받는다.
     * 경합 구간이 한 행뿐이라 재시도 비용이 드는 낙관적 락보다 비관적 락이 유리하다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RevisionSeq r where r.id = :id")
    Optional<RevisionSeq> findWithLockById(int id);

    default long issueNext() {
        return findWithLockById(RevisionSeq.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("revision_seq 초기 행이 없습니다."))
                .next();
    }

    default long current() {
        return findById(RevisionSeq.SINGLETON_ID).map(RevisionSeq::getCur).orElse(0L);
    }
}
