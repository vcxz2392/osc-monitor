package com.osc.monitor;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 컨테이너를 static 블록에서 한 번만 띄운다.
 *
 * <p>{@code @Testcontainers} + {@code @Container} 는 컨테이너 수명을 테스트 클래스 단위로 관리하는데,
 * 스프링 컨텍스트는 클래스 사이에 캐시되어 살아남는다. 첫 클래스가 끝나며 컨테이너가 내려가면
 * 두 번째 클래스부터 사라진 포트로 붙어 전부 연결 예외가 난다. 정리는 Ryuk 에 맡긴다.
 *
 * <p>데이터는 컨텍스트당 한 번 생성기가 적재한다(시드 고정이라 매번 같은 데이터다).
 *
 * <p>테스트 기본값은 {@code application-test.yml} 에 둔다. {@code @DynamicPropertySource} 는
 * {@code @TestPropertySource} 보다 우선순위가 높아, 여기서 정하면 개별 테스트가 뒤집을 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("mysqld", "--innodb-buffer-pool-size=256M")
            .withUrlParam("rewriteBatchedStatements", "true");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.generator.enabled", () -> true);
    }
}
