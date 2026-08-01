-- 델타: WHERE rev > ? ORDER BY rev, id
-- 리비전이 단조 증가하므로 since 이후 구간은 항상 좁다.
CREATE INDEX idx_rev ON resource (rev);
