package com.osc.monitor.revision.repository;

import com.osc.monitor.revision.repository.entity.RevisionSeq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevisionRepository extends JpaRepository<RevisionSeq, Integer> {

    default long current() {
        return findById(RevisionSeq.SINGLETON_ID).map(RevisionSeq::getCur).orElse(0L);
    }
}
