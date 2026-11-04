package repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import exception.StorageException;

import model.Account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Repository
public class AccountRepository {
    public static final Object LOCK = new Object();
    private final Path path;
    private final ObjectMapper mapper;

    public AccountRepository(
            @Value("${accounts.storage-path:data/accounts.json}") String path,
            ObjectMapper mapper) {
        this.path = Path.of(path).toAbsolutePath();
        this.mapper = mapper;
    }

    public List<Account> findAll() {
        synchronized (LOCK) {
            if (Files.notExists(path)) return new ArrayList<>();
            try {
                List<Account> data =
                        mapper.readValue(path.toFile(), new TypeReference<List<Account>>() {});
                if (data == null
                        || data.stream()
                                .anyMatch(
                                        a ->
                                                a == null
                                                        || a.id() == null
                                                        || a.passwordHash() == null
                                                        || (!"ADMIN".equals(a.role())
                                                                && !"CUSTOMER".equals(a.role()))
                                                        || ("CUSTOMER".equals(a.role())
                                                                && a.customerId() == null)))
                    throw new IOException("Invalid accounts");
                return data;
            } catch (IOException e) {
                throw new StorageException("Không thể đọc tài khoản.", e);
            }
        }
    }

    public void save(Account account) {
        synchronized (LOCK) {
            List<Account> all = findAll();
            all.add(account);
            writeAll(all);
        }
    }

    public void replacePassword(String accountId, String passwordHash) {
        synchronized (LOCK) {
            List<Account> all = findAll();
            for (int i = 0; i < all.size(); i++) {
                Account account = all.get(i);
                if (account.id().equals(accountId)) {
                    all.set(i, new Account(account.id(), account.email(), passwordHash,
                            account.role(), account.customerId()));
                    writeAll(all);
                    return;
                }
            }
            throw new handler.ApiException(404, "Không tìm thấy tài khoản.");
        }
    }

    private void writeAll(List<Account> all) {
        Path tmp = null;
        try {
            Files.createDirectories(path.getParent());
            tmp = Files.createTempFile(path.getParent(), ".accounts-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), all);
            try {
                Files.move(
                        tmp,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Không thể lưu tài khoản.", e);
        } finally {
            if (tmp != null)
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
        }
    }
}
