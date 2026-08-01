package com.osc.monitor.revision.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 전역 단조 증가 리비전. 한 행만 존재한다. */
@Entity
@Getter
@Table(name = "revision_seq")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionSeq {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private long cur;

    private RevisionSeq(long cur) {
        this.id = SINGLETON_ID;
        this.cur = cur;
    }

    public static RevisionSeq of(long cur) {
        return new RevisionSeq(cur);
    }

    public long next() {
        return ++cur;
    }
}
