package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class AssignmentFileStorageService {
    private static final long MAX_EXPANDED_PACKAGE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_PACKAGE_ENTRIES = 2_000;

    private final Path storageRoot;
    private final long maxFileSizeBytes;

    @Autowired
    public AssignmentFileStorageService(
            @Value("${app.assignment-files.storage-dir:${user.home}/.remote-learning-platform/assignment-resources}") Path storageRoot,
            @Value("${app.assignment-files.max-file-size:10MB}") String maxFileSize) {
        this(storageRoot, parseSize(maxFileSize));
    }

    AssignmentFileStorageService(Path storageRoot, long maxFileSizeBytes) {
        if (maxFileSizeBytes <= 0) throw new IllegalArgumentException("Maximum file size must be positive");
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty file to upload.");
        if (file.getSize() > maxFileSizeBytes) throw sizeLimitException();

        final byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new FileStorageException("The uploaded file could not be read.", ex);
        }
        if (content.length == 0) throw new IllegalArgumentException("Choose a non-empty file to upload.");
        if (content.length > maxFileSizeBytes) throw sizeLimitException();

        String displayName = safeDisplayName(file.getOriginalFilename());
        String contentType = validateContent(displayName, content);
        String storageId = UUID.randomUUID().toString();
        Path target = resolveStoragePath(storageId);
        try {
            Files.createDirectories(storageRoot);
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new StoredFile(storageId, displayName, content.length, contentType);
        } catch (IOException ex) {
            throw new FileStorageException("The file could not be stored.", ex);
        }
    }

    public Path pathForDownload(String storageId) {
        Path candidate = resolveStoragePath(storageId);
        try {
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate)) {
                throw new FileStorageException("The stored file is unavailable.", null);
            }
            Path realRoot = storageRoot.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new IllegalArgumentException("Invalid file storage identifier.");
            }
            return realFile;
        } catch (IOException ex) {
            throw new FileStorageException("The stored file is unavailable.", ex);
        }
    }

    public void delete(String storageId) {
        Path candidate = resolveStoragePath(storageId);
        try {
            Files.deleteIfExists(candidate);
        } catch (IOException ex) {
            throw new FileStorageException("The stored file could not be removed.", ex);
        }
    }

    Path resolveStoragePath(String storageId) {
        if (storageId == null || !storageId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Invalid file storage identifier.");
        }
        Path resolved = storageRoot.resolve(storageId).normalize();
        if (!resolved.startsWith(storageRoot) || resolved.equals(storageRoot)) {
            throw new IllegalArgumentException("Invalid file storage identifier.");
        }
        return resolved;
    }

    private String safeDisplayName(String suppliedName) {
        if (suppliedName == null || suppliedName.isBlank()) {
            throw new IllegalArgumentException("The uploaded file must have a filename.");
        }
        String basename = suppliedName.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        StringBuilder safe = new StringBuilder();
        basename.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != '/' && codePoint != '\\')
                .forEach(safe::appendCodePoint);
        String result = safe.toString().strip();
        if (result.isBlank() || result.length() > 255 || result.equals(".") || result.equals("..")) {
            throw new IllegalArgumentException("The uploaded filename is invalid or too long.");
        }
        return result;
    }

    private String validateContent(String filename, byte[] content) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> {
                if (!startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                    throw unsupportedFile();
                }
                yield "application/pdf";
            }
            case "txt" -> {
                validateUtf8Text(content);
                yield "text/plain";
            }
            case "docx" -> {
                validateOfficePackage(content, "word/document.xml");
                yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            case "pptx" -> {
                validateOfficePackage(content, "ppt/presentation.xml");
                yield "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
            default -> throw unsupportedFile();
        };
    }

    private void validateUtf8Text(byte[] content) {
        for (byte value : content) {
            if (value == 0) throw unsupportedFile();
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException ex) {
            throw unsupportedFile();
        }
    }

    private void validateOfficePackage(byte[] content, String requiredEntry) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') throw unsupportedFile();
        boolean requiredEntryFound = false;
        int entryCount = 0;
        long expandedBytes = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_PACKAGE_ENTRIES) throw unsupportedFile();
                String entryName = entry.getName().replace('\\', '/');
                if (entryName.equals(requiredEntry)) requiredEntryFound = true;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > MAX_EXPANDED_PACKAGE_BYTES) throw unsupportedFile();
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw unsupportedFile();
        }
        if (!requiredEntryFound) throw unsupportedFile();
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) return false;
        }
        return true;
    }

    private IllegalArgumentException unsupportedFile() {
        return new IllegalArgumentException("Unsupported or invalid file. Upload a valid PDF, TXT, DOCX, or PPTX file.");
    }

    private IllegalArgumentException sizeLimitException() {
        double megabytes = maxFileSizeBytes / (1024.0 * 1024.0);
        String limit = megabytes >= 1
                ? (megabytes == Math.floor(megabytes)
                    ? String.format(Locale.ROOT, "%.0f MB", megabytes)
                    : String.format(Locale.ROOT, "%.1f MB", megabytes))
                : maxFileSizeBytes >= 1024
                    ? String.format(Locale.ROOT, "%.0f KB", maxFileSizeBytes / 1024.0)
                    : maxFileSizeBytes + " B";
        return new IllegalArgumentException("Each file must be no larger than " + limit + ".");
    }

    private static long parseSize(String value) {
        try {
            return org.springframework.util.unit.DataSize.parse(value).toBytes();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid assignment file size configuration.", ex);
        }
    }

    public record StoredFile(String storageId, String originalFilename, long fileSize, String contentType) {}
}
