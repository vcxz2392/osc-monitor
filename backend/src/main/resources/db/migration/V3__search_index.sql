-- 검색: WHERE name LIKE 'q%' OR name LIKE '%-q%' ORDER BY name, id
-- 앞부분 매칭은 범위 스캔으로 끝나고, 토큰 매칭은 이 인덱스를 순서대로 훑는다.
CREATE INDEX idx_search ON resource (name);
