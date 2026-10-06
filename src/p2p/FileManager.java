package p2p;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class FileManager {
    private final Map<String, SharedFile> files = new HashMap<>();

    public synchronized SharedFile share(Path path) throws IOException {
        SharedFile file = SharedFile.createSeed(path);
        files.put(file.fileId, file);
        return file;
    }

    public synchronized SharedFile get(String fileId) {
        return files.get(fileId);
    }

    public synchronized SharedFile findByName(String fileName) {
        for (SharedFile f : files.values()) {
            if (f.fileName.equals(fileName)) return f;
        }
        return null;
    }

    public synchronized Collection<SharedFile> all() {
        return new ArrayList<>(files.values());
    }

    public synchronized SharedFile createPartial(PeerInfo.Advertisement ad) throws IOException {
        SharedFile existing = files.get(ad.fileId);
        if (existing != null) return existing;

        Path output = Path.of("downloads", ad.fileName + "." + ad.fileId.substring(0, 8) + ".part");
        SharedFile partial = SharedFile.createPartial(
                ad.fileId,
                ad.fileName,
                ad.fileSize,
                ad.pieceSize,
                ad.pieceCount,
                ad.wholeHash,
                ad.pieceHashes,
                output);
        files.put(ad.fileId, partial);
        return partial;
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) md.update(buffer, 0, n);
            }
        }
        return SharedFile.hex(md.digest());
    }
}
