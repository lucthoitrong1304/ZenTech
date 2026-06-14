package hcmute.edu.zentech;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@SpringBootTest
class ZenTechApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        System.out.println("--- DUMPING ACCOUNTS AND IMAGES ---");
        
        System.out.println("\n=== EMPLOYEES ===");
        List<Map<String, Object>> employees = jdbcTemplate.queryForList(
            "SELECT e.fullName, e.image_url, u.email, u.role FROM employees e JOIN account_users u ON e.account_id = u.account_id"
        );
        for (Map<String, Object> emp : employees) {
            System.out.println(emp);
        }

        System.out.println("\n=== CUSTOMERS ===");
        List<Map<String, Object>> customers = jdbcTemplate.queryForList(
            "SELECT c.fullName, c.image_url, u.email, u.role FROM customers c JOIN account_users u ON c.account_id = u.account_id"
        );
        for (Map<String, Object> cust : customers) {
            System.out.println(cust);
        }

        System.out.println("--- DUMPING COMPLETE ---");
    }

}
