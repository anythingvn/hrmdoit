package vn.doit.hrm.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Hrm.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    private final Contract contract = new Contract();

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public Contract getContract() {
        return contract;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Contract {

        private List<Integer> termOptionsMonths = List.of(12, 24, 36);

        public List<Integer> getTermOptionsMonths() {
            return termOptionsMonths;
        }

        public void setTermOptionsMonths(List<Integer> termOptionsMonths) {
            this.termOptionsMonths = termOptionsMonths;
        }
    }

    public static class Liquibase {

        private Boolean asyncStart;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }
    // jhipster-needle-application-properties-property-class
}
