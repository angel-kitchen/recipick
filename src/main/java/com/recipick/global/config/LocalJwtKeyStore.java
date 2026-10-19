package com.recipick.global.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import javax.crypto.SecretKey;

class LocalJwtKeyStore {

    synchronized SecretKey loadOrCreate(Path keyPath) throws IOException {
        Path directory = keyPath.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        boolean posix = Files.getFileStore(directory).supportsFileAttributeView("posix");
        if (posix) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
        Path lockPath = directory.resolve(keyPath.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            if (!lock.isValid()) {
                throw new IOException("로컬 JWT 키 파일의 잠금을 획득하지 못했습니다.");
            }
            if (!Files.exists(keyPath)) {
                String secret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
                if (posix) {
                    Files.createFile(keyPath, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
                } else {
                    Files.createFile(keyPath);
                }
                Files.writeString(keyPath, secret);
            }
            if (posix) {
                Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
            }
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(Files.readString(keyPath).trim()));
        }
    }
}
