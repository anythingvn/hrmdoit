package vn.doit.hrm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import vn.doit.hrm.config.AsyncSyncConfiguration;
import vn.doit.hrm.config.DatabaseTestcontainer;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
        HrmApp.class,
        AsyncSyncConfiguration.class,
        vn.doit.hrm.config.JacksonHibernateConfiguration.class,
        DatabaseTestcontainer.class,
    }
)
public @interface IntegrationTest {}
