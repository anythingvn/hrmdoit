package vn.doit.hrm.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.repository.CompanySettingRepository;
import vn.doit.hrm.service.dto.BrandingDTO;
import vn.doit.hrm.service.dto.CompanySettingDTO;

/**
 * Cài đặt công ty (một dòng, id = 1) thay cho {@code application.company.*}: tên công ty và người đại diện in trên phiếu lương,
 * báo cáo, hợp đồng, quyết định; tên viết tắt, khẩu hiệu và logo hiện trên web. Đổi trên giao diện có hiệu lực ngay.
 * <p>
 * Dòng được tạo ở lần đọc đầu tiên, lấy giá trị từ {@code application.company.*}, bằng {@code insert … on conflict do nothing}
 * trong giao dịch đang chạy (không mở giao dịch thứ hai: không chiếm thêm kết nối, pool 1 kết nối vẫn chạy). Nhiều request đầu
 * tiên chạy cùng lúc chỉ tạo một dòng. Trong giao dịch chỉ đọc không ghi được: trả giá trị từ cấu hình, chưa lưu.
 * Logo chỉ hiện trên web, không in lên giấy tờ.
 */
@Service
public class CompanySettingService {

    public static final String DEFAULT_SHORT_NAME = "DOIT HRM";
    public static final String DEFAULT_TAGLINE = "Do it if you can";

    /** Logo tối đa 1 MB. */
    public static final int MAX_LOGO_BYTES = 1_048_576;

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String SVG = "image/svg+xml";
    private static final Set<String> LOGO_TYPES = Set.of(PNG, JPEG, SVG);

    private static final String DEFAULT_LOGO_RESOURCE = "/branding/default-logo.png";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    static final int NAME_MAX = 255;
    static final int SHORT_NAME_MAX = 30;
    static final int TAGLINE_MAX = 100;
    static final int REPRESENTATIVE_MAX = 100;

    /** Ảnh logo kèm kiểu file và phiên bản (ETag). */
    public record Logo(byte[] bytes, String contentType, long version) {}

    private final CompanySettingRepository repository;
    private final ApplicationProperties properties;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    private volatile byte[] defaultLogo;

    public CompanySettingService(
        CompanySettingRepository repository,
        ApplicationProperties properties,
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Dòng cài đặt; chưa có thì tạo từ {@code application.company.*}. */
    public CompanySetting current() {
        Optional<CompanySetting> found = repository.findById(CompanySetting.ID);
        if (found.isPresent()) {
            return found.orElseThrow();
        }
        CompanySetting initial = fromProperties();
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            // Giao dịch chỉ đọc (vd. xuất báo cáo): dùng giá trị ban đầu, lần ghi / đọc ngoài giao dịch sau sẽ tạo dòng.
            return initial;
        }
        // Tham gia giao dịch đang có, hoặc mở giao dịch mới khi gọi ngoài giao dịch.
        tx.executeWithoutResult(s -> {
            jdbc.update(
                "insert into company_setting (id, company_name, short_name, tagline, representative_name, representative_title) " +
                "values (?, ?, ?, ?, ?, ?) on conflict (id) do nothing",
                CompanySetting.ID,
                initial.getCompanyName(),
                initial.getShortName(),
                initial.getTagline(),
                initial.getRepresentativeName(),
                initial.getRepresentativeTitle()
            );
        });
        return repository.findById(CompanySetting.ID).orElseThrow();
    }

    /** Giá trị ban đầu từ {@code application.company.*} (chưa lưu). */
    private CompanySetting fromProperties() {
        ApplicationProperties.Company c = properties.getCompany();
        String name = blankToNull(c.getName());
        CompanySetting s = new CompanySetting();
        s.setCompanyName(cut(name == null ? "CÔNG TY" : name, NAME_MAX));
        s.setShortName(DEFAULT_SHORT_NAME);
        s.setTagline(DEFAULT_TAGLINE);
        s.setRepresentativeName(cut(blankToNull(c.getRepresentativeName()), REPRESENTATIVE_MAX));
        s.setRepresentativeTitle(cut(blankToNull(c.getRepresentativeTitle()), REPRESENTATIVE_MAX));
        return s;
    }

    public String companyName() {
        return current().getCompanyName();
    }

    /** Người đại diện ký hợp đồng, quyết định; chuỗi rỗng khi chưa nhập (văn bản báo thiếu). */
    public String representativeName() {
        return nullToEmpty(current().getRepresentativeName());
    }

    public String representativeTitle() {
        return nullToEmpty(current().getRepresentativeTitle());
    }

    public CompanySettingDTO view() {
        return toDto(current());
    }

    /** Thông tin công khai; tên viết tắt trống thì dùng "DOIT HRM". */
    public BrandingDTO branding() {
        CompanySetting s = current();
        String shortName = blankToNull(s.getShortName());
        return new BrandingDTO(
            s.getCompanyName(),
            shortName == null ? DEFAULT_SHORT_NAME : shortName,
            nullToEmpty(s.getTagline()),
            s.logoVersion()
        );
    }

    @Transactional
    public CompanySettingDTO update(CompanySettingDTO in) {
        String name = blankToNull(in.companyName());
        if (name == null) {
            throw new EmployeeRuleException("Cần nhập tên công ty", "companynamerequired");
        }
        String shortName = blankToNull(in.shortName());
        String tagline = blankToNull(in.tagline());
        String representativeName = blankToNull(in.representativeName());
        String representativeTitle = blankToNull(in.representativeTitle());
        if (
            name.length() > NAME_MAX ||
            tooLong(shortName, SHORT_NAME_MAX) ||
            tooLong(tagline, TAGLINE_MAX) ||
            tooLong(representativeName, REPRESENTATIVE_MAX) ||
            tooLong(representativeTitle, REPRESENTATIVE_MAX)
        ) {
            throw new EmployeeRuleException(
                "Thông tin công ty quá dài: tên công ty tối đa 255 ký tự, tên viết tắt 30, khẩu hiệu 100, người đại diện và chức danh 100",
                "companysettinginvalid"
            );
        }
        CompanySetting s = current();
        s.setCompanyName(name);
        s.setShortName(shortName);
        s.setTagline(tagline);
        s.setRepresentativeName(representativeName);
        s.setRepresentativeTitle(representativeTitle);
        return toDto(repository.save(s));
    }

    /** Lưu logo PNG, JPG hoặc SVG tối đa 1 MB; kiểm tra cả kiểu khai báo lẫn byte đầu file. */
    @Transactional
    public CompanySettingDTO uploadLogo(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw invalidType();
        }
        if (bytes.length > MAX_LOGO_BYTES) {
            throw new EmployeeRuleException("Logo tối đa 1 MB", "logotoolarge");
        }
        String declared = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!LOGO_TYPES.contains(declared) || !declared.equals(detect(bytes))) {
            throw invalidType();
        }
        CompanySetting s = current();
        s.setLogoContentType(declared);
        s.setLogoUpdatedAt(nextVersion(s.getLogoUpdatedAt()));
        jdbc.update("update company_setting set logo = ? where id = ?", bytes, CompanySetting.ID);
        return toDto(repository.save(s));
    }

    /** Trở lại logo mặc định; phiên bản logo vẫn đổi để trình duyệt tải lại ảnh. */
    @Transactional
    public CompanySettingDTO resetLogo() {
        CompanySetting s = current();
        s.setLogoContentType(null);
        s.setLogoUpdatedAt(nextVersion(s.getLogoUpdatedAt()));
        jdbc.update("update company_setting set logo = null where id = ?", CompanySetting.ID);
        return toDto(repository.save(s));
    }

    /** Phiên bản logo hiện tại (để trả 304 mà không đọc ảnh). */
    public long logoVersion() {
        return current().logoVersion();
    }

    /** Logo đã tải lên, chưa có thì logo mặc định. Ảnh, kiểu file và phiên bản đọc trong cùng một câu lệnh. */
    public Logo logo() {
        current();
        Logo stored = jdbc.query(
            "select logo, logo_content_type, logo_updated_at from company_setting where id = ?",
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Timestamp at = rs.getTimestamp(3, Calendar.getInstance(UTC)); // cùng múi giờ lưu với hibernate.jdbc.time_zone
                long version = at == null ? 0L : at.toInstant().toEpochMilli();
                byte[] bytes = rs.getBytes(1);
                String type = rs.getString(2);
                return bytes == null || type == null ? new Logo(defaultLogo(), PNG, version) : new Logo(bytes, type, version);
            },
            CompanySetting.ID
        );
        return stored != null ? stored : new Logo(defaultLogo(), PNG, 0L);
    }

    private byte[] defaultLogo() {
        byte[] b = defaultLogo;
        if (b == null) {
            try (InputStream in = CompanySettingService.class.getResourceAsStream(DEFAULT_LOGO_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Thiếu logo mặc định " + DEFAULT_LOGO_RESOURCE);
                }
                b = in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            defaultLogo = b;
        }
        return b;
    }

    /** Kiểu ảnh theo byte đầu file: PNG {@code 89 50 4E 47}, JPG {@code FF D8 FF}, SVG là văn bản UTF-8 chứa {@code <svg}. */
    static String detect(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return PNG;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b))
                .toString();
            return text.toLowerCase(Locale.ROOT).contains("<svg") ? SVG : null;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private Instant nextVersion(Instant previous) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (previous != null && !now.isAfter(previous)) {
            return previous.truncatedTo(ChronoUnit.MILLIS).plusMillis(1);
        }
        return now;
    }

    private static CompanySettingDTO toDto(CompanySetting s) {
        return new CompanySettingDTO(
            s.getCompanyName(),
            s.getShortName(),
            s.getTagline(),
            s.getRepresentativeName(),
            s.getRepresentativeTitle(),
            s.logoVersion()
        );
    }

    private static EmployeeRuleException invalidType() {
        return new EmployeeRuleException("Logo phải là ảnh PNG, JPG hoặc SVG", "logoinvalidtype");
    }

    private static boolean tooLong(String s, int max) {
        return s != null && s.length() > max;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
