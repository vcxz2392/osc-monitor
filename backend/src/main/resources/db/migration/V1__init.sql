-- 4계층(Cluster 0 / Node 1 / Namespace 2 / Pod 3)을 한 테이블에 담는다.
-- 세컨더리 인덱스는 그 인덱스를 쓰는 쿼리가 생기는 시점에 추가한다.
CREATE TABLE resource
(
    id           BIGINT       NOT NULL,
    parent_id    BIGINT       NULL,
    type         TINYINT      NOT NULL,
    name         VARCHAR(120) NOT NULL,
    status       TINYINT      NOT NULL,
    path         VARCHAR(64)  NOT NULL,               -- 자기 포함 조상 id 경로 '/1/1001/100001/10000001/'
    updated_at   DATETIME(3)  NOT NULL,               -- 화면 표시용
    rev          BIGINT       NOT NULL,               -- 델타 갱신 기준
    error_cnt    INT          NOT NULL DEFAULT 0,     -- 하위 집계
    warn_cnt     INT          NOT NULL DEFAULT 0,
    child_cnt    INT          NOT NULL DEFAULT 0,     -- 직속 자식 수
    leaf_cnt     INT          NOT NULL DEFAULT 0,     -- 하위 파드 총수. 오류·경고의 분모
    metrics_json JSON         NULL,                   -- 타입별 표시용 지표
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 전역 단조 증가 리비전. 한 행만 존재한다.
CREATE TABLE revision_seq
(
    id  TINYINT NOT NULL,
    cur BIGINT  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO revision_seq (id, cur)
VALUES (1, 0);
