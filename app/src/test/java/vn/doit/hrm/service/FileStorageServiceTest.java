package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

class FileStorageServiceTest {

    @TempDir
    Path root;

    private FileStorageService storage;

    @BeforeEach
    void init() {
        storage = new FileStorageService(root);
    }

    @Test
    void maliciousNameStaysInsideRoot() throws IOException {
        String path = storage.store("employees/7", "../../etc/passwd.pdf", new ByteArrayInputStream("%PDF-x".getBytes(StandardCharsets.UTF_8)));

        assertThat(path).doesNotContain("..").startsWith("employees/7/").endsWith(".pdf");
        assertThat(path).doesNotContain("passwd");
        Path file = root.resolve(path).normalize();
        assertThat(file).startsWith(root);
        assertThat(Files.readString(file)).isEqualTo("%PDF-x");
    }

    @Test
    void loadReturnsStoredContent() throws IOException {
        String path = storage.store("employees/1", "a.png", new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        Resource r = storage.load(path);
        assertThat(r.getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void loadOutsideRootIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> storage.load("../x"));
        assertThrows(IllegalArgumentException.class, () -> storage.load("employees/../../x"));
    }

    @Test
    void storeFolderOutsideRootIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> storage.store("../evil", "a.pdf", new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void deleteRemovesFileAndIgnoresMissing() throws IOException {
        String path = storage.store("employees/1", "a.pdf", new ByteArrayInputStream(new byte[] { 1 }));
        storage.delete(path);
        assertThat(Files.exists(root.resolve(path))).isFalse();
        storage.delete(path);
    }
}
