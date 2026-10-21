package com.recipick.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalJwtKeyStoreTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("기존 상위 폴더의 권한을 바꾸지 않고 키 파일만 보호한다")
    void preservesExistingDirectoryPermissions() throws Exception {
        // given
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        var original = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(directory, original);
        Path keyPath = directory.resolve("jwt-secret");
        // when
        LocalJwtKeyStore.loadOrCreate(keyPath);
        // then
        assertThat(Files.getPosixFilePermissions(directory)).isEqualTo(original);
        assertThat(Files.getPosixFilePermissions(keyPath))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    @DisplayName("새 키 전용 폴더만 소유자 접근 권한으로 생성한다")
    void restrictsNewDirectory() throws Exception {
        // given
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path keyPath = directory.resolve("new-keys/jwt-secret");
        // when
        LocalJwtKeyStore.loadOrCreate(keyPath);
        // then
        assertThat(Files.getPosixFilePermissions(keyPath.getParent()))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    }

    @Test
    @DisplayName("동시에 키를 요청해도 동일한 키를 반환한다")
    void concurrentLoadsUseSameKey() throws Exception {
        // given
        Path keyPath = directory.resolve("jwt-secret");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<SecretKey> load = () -> LocalJwtKeyStore.loadOrCreate(keyPath);
        try {
            // when
            Future<SecretKey> first = executor.submit(load);
            Future<SecretKey> second = executor.submit(load);
            // then
            assertThat(first.get().getEncoded()).isEqualTo(second.get().getEncoded());
        } finally {
            executor.shutdownNow();
        }
    }
}
