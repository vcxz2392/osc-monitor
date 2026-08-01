-- 펼치기: WHERE parent_id = ? ORDER BY name, id
-- 같은 부모 아래 이름은 유일하다. 유니크로 두면 인덱스 하나로 정렬과 무결성을 함께 얻는다.
CREATE UNIQUE INDEX uk_children ON resource (parent_id, name);
