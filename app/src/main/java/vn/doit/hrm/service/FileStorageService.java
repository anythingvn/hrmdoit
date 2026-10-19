package vn.doit.hrm.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import vn.doit.hrm.config.ApplicationProperties;

/**
 * Lưu file trên đĩa dưới thư mục gốc cấu hình. Tên file trên đĩa là UUID; tên gốc chỉ lưu trong DB.
 * Mọi đường dẫn được chuẩn hóa và phải nằm trong thư mục gốc.
 */
@Service
public class FileStorageService {

    private final Path root;

    @Autowired
    public FileStorageService(ApplicationProperties properties) {
        this(Path.of(properties.getStorage().getRoot()));
    }

    public FileStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Lưu luồng vào {@code folder/uuid.ext}; trả về đường dẫn tương đối (dấu '/'). */
    public String store(String folder, String originalName, InputStream in) {
        Path dir = resolveInside(folder);
        String name = UUID.randomUUID() + extensionOf(originalName);
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn không hợp lệ");
        }
        try {
            Files.createDirectories(dir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return root.relativize(target).toString().replace('\\', '/');
    }

    public Resource load(String storagePath) {
        Path file = resolveInside(storagePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Không tìm thấy file");
        }
        return new PathResource(file);
    }

    /** Xóa file; bỏ qua nếu không còn. */
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolveInside(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolveInside(String relative) {
        if (relative == null || relative.isBlank() || relative.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Đường dẫn không hợp lệ");
        }
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new IllegalArgumentException("Đường dẫn nằm ngoài thư mục lưu trữ");
        }
        return p;
    }

    /** Đuôi file (kèm dấu chấm) chỉ gồm chữ/số, tối đa 10 ký tự; không hợp lệ thì bỏ. */
    static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        String base = originalName.substring(Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1);
        int dot = base.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        String ext = base.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? "." + ext : "";
    }
}
