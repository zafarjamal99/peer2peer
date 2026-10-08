package p2p;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class SharedFile {
    public static final int PIECE_SIZE = 256 * 1024;

    public final String fileId;
    public final String fileName;
    public final Path path;
    public final long fileSize;
    public final int pieceSize;
    public final int pieceCount;
    public final String wholeHash;
    public final List<String> pieceHashes;
    private final BitSet bitmap;

    private SharedFile(
            String fileId,
            String fileName,
            Path path,
            long fileSize,
            int pieceSize,
            int pieceCount,
            String wholeHash,
            List<String> pieceHashes,
            BitSet bitmap) {

        this.fileId = fileId;
        this.fileName = fileName;
        this.path = path;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.pieceCount = pieceCount;
        this.wholeHash = wholeHash;
        this.pieceHashes = List.copyOf(pieceHashes);
        this.bitmap = bitmap;
    }

    public static SharedFile createSeed(Path path) throws IOException {
        long size = Files.size(path);

        int count = (int) ((size + PIECE_SIZE - 1) / PIECE_SIZE);

        List<String> hashes = new ArrayList<>(count);

        MessageDigest whole = sha256();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[PIECE_SIZE];

            int index = 0;
            int n;

            while ((n = readUpTo(in, buffer)) > 0) {
                whole.update(buffer, 0, n);
                hashes.add(hex(sha256Bytes(buffer, 0, n)));
                index++;
            }
        }

        String wholeHash = hex(whole.digest());

        BitSet bitmap = new BitSet(count);
        bitmap.set(0, count);

        return new SharedFile(
                wholeHash,
                path.getFileName().toString(),
                path,
                size,
                PIECE_SIZE,
                count,
                wholeHash,
                hashes,
                bitmap
        );
    }

    public static SharedFile createPartial(
            String fileId,
            String fileName,
            long fileSize,
            int pieceSize,
            int pieceCount,
            String wholeHash,
            List<String> pieceHashes,
            Path partialPath) throws IOException {

        if (!Files.exists(partialPath)) {
            try (RandomAccessFile raf =
                         new RandomAccessFile(partialPath.toFile(), "rw")) {
                raf.setLength(fileSize);
            }
        }

        return new SharedFile(
                fileId,
                fileName,
                partialPath,
                fileSize,
                pieceSize,
                pieceCount,
                wholeHash,
                pieceHashes,
                new BitSet(pieceCount)
        );
    }

    public synchronized boolean hasPiece(int index) {
        return bitmap.get(index);
    }

    public synchronized void markPiece(int index) {
        bitmap.set(index);
    }

    public synchronized int completedPieces() {
        return bitmap.cardinality();
    }

    public synchronized boolean isComplete() {
        return bitmap.cardinality() == pieceCount;
    }

    public synchronized BitSet bitmapCopy() {
        return (BitSet) bitmap.clone();
    }

    public long pieceLength(int index) {
        long start = (long) index * pieceSize;
        return Math.min(pieceSize, fileSize - start);
    }

    public byte[] readPiece(int index) throws IOException {
        int length = (int) pieceLength(index);

        byte[] data = new byte[length];

        try (RandomAccessFile raf =
                     new RandomAccessFile(path.toFile(), "r")) {

            raf.seek((long) index * pieceSize);
            raf.readFully(data);
        }

        return data;
    }

    public void writePiece(int index, byte[] data) throws IOException {
        try (RandomAccessFile raf =
                     new RandomAccessFile(path.toFile(), "rw")) {

            raf.seek((long) index * pieceSize);
            raf.write(data);
        }
    }

    public String verifyWholeFile() throws IOException {
        return FileManager.sha256(path);
    }

    public boolean exists() {
        return Files.isRegularFile(path);
    }

    public void validateMetadata() throws IOException {
        if (!exists()) {
            throw new IOException("File does not exist: " + path);
        }

        long actualSize = Files.size(path);

        if (actualSize != fileSize) {
            throw new IOException(
                    "File size mismatch for " + fileName +
                    ": expected " + fileSize +
                    ", found " + actualSize
            );
        }

        if (pieceHashes.size() != pieceCount) {
            throw new IOException(
                    "Piece metadata mismatch for " + fileName
            );
        }
    }

    private static int readUpTo(InputStream in, byte[] buffer) throws IOException {
        int total = 0;

        while (total < buffer.length) {
            int n = in.read(buffer, total, buffer.length - total);

            if (n < 0) {
                break;
            }

            if (n == 0) {
                continue;
            }

            total += n;
        }

        return total;
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] sha256Bytes(byte[] data, int off, int len) {
        MessageDigest md = sha256();
        md.update(data, off, len);
        return md.digest();
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}package p2p;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class SharedFile {
    public static final int PIECE_SIZE = 256 * 1024;

    public final String fileId;
    public final String fileName;
    public final Path path;
    public final long fileSize;
    public final int pieceSize;
    public final int pieceCount;
    public final String wholeHash;
    public final List<String> pieceHashes;

    private final BitSet bitmap;

    private SharedFile(
            String fileId,
            String fileName,
            Path path,
            long fileSize,
            int pieceSize,
            int pieceCount,
            String wholeHash,
            List<String> pieceHashes,
            BitSet bitmap) {

        this.fileId = fileId;
        this.fileName = fileName;
        this.path = path;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.pieceCount = pieceCount;
        this.wholeHash = wholeHash;
        this.pieceHashes = List.copyOf(pieceHashes);
        this.bitmap = bitmap;
    }

    public static SharedFile createSeed(Path path)
            throws IOException {

        long size = Files.size(path);

        int count =
                (int) ((size + PIECE_SIZE - 1)
                        / PIECE_SIZE);

        List<String> hashes =
                new ArrayList<>(count);

        MessageDigest whole = sha256();

        try (InputStream in =
                     new BufferedInputStream(
                             Files.newInputStream(path))) {

            byte[] buffer =
                    new byte[PIECE_SIZE];

            int index = 0;
            int n;

            while ((n = readUpTo(in, buffer)) > 0) {

                whole.update(buffer, 0, n);

                hashes.add(
                        hex(
                                sha256Bytes(
                                        buffer,
                                        0,
                                        n)));

                index++;
            }
        }

        String wholeHash =
                hex(whole.digest());

        BitSet bitmap =
                new BitSet(count);

        bitmap.set(0, count);

        return new SharedFile(
                wholeHash,
                path.getFileName().toString(),
                path,
                size,
                PIECE_SIZE,
                count,
                wholeHash,
                hashes,
                bitmap);
    }

    public static SharedFile createPartial(
            String fileId,
            String fileName,
            long fileSize,
            int pieceSize,
            int pieceCount,
            String wholeHash,
            List<String> pieceHashes,
            Path partialPath)
            throws IOException {

        if (!Files.exists(partialPath)) {

            try (RandomAccessFile raf =
                         new RandomAccessFile(
                                 partialPath.toFile(),
                                 "rw")) {

                raf.setLength(fileSize);
            }
        }

        return new SharedFile(
                fileId,
                fileName,
                partialPath,
                fileSize,
                pieceSize,
                pieceCount,
                wholeHash,
                pieceHashes,
                new BitSet(pieceCount));
    }

    public synchronized boolean hasPiece(int index) {
        return bitmap.get(index);
    }

    public synchronized void markPiece(int index) {
        bitmap.set(index);
    }

    public synchronized int completedPieces() {
        return bitmap.cardinality();
    }

    public synchronized boolean isComplete() {
        return bitmap.cardinality() == pieceCount;
    }

    public synchronized BitSet bitmapCopy() {
        return (BitSet) bitmap.clone();
    }

    public long pieceLength(int index) {

        long start =
                (long) index * pieceSize;

        return Math.min(
                pieceSize,
                fileSize - start);
    }

    public byte[] readPiece(int index)
            throws IOException {

        int length =
                (int) pieceLength(index);

        byte[] data =
                new byte[length];

        try (RandomAccessFile raf =
                     new RandomAccessFile(
                             path.toFile(),
                             "r")) {

            raf.seek(
                    (long) index * pieceSize);

            raf.readFully(data);
        }

        return data;
    }

    public void writePiece(
            int index,
            byte[] data)
            throws IOException {

        try (RandomAccessFile raf =
                     new RandomAccessFile(
                             path.toFile(),
                             "rw")) {

            raf.seek(
                    (long) index * pieceSize);

            raf.write(data);
        }
    }

    /*
     * Calculate SHA-256 of the complete file.
     */
    public String verifyWholeFile()
            throws IOException {

        return FileManager.sha256(path);
    }

    /*
     * Check whether the complete file matches
     * the expected SHA-256 hash.
     */
    public boolean isWholeFileValid()
            throws IOException {

        return FileManager.verifySha256(
                path,
                wholeHash);
    }

    /*
     * Calculate SHA-256 of one piece.
     */
    public String verifyPieceHash(
            int index)
            throws IOException {

        byte[] data =
                readPiece(index);

        return hex(
                sha256Bytes(
                        data,
                        0,
                        data.length));
    }

    /*
     * Verify one piece against the expected
     * piece hash received during discovery.
     */
    public boolean isPieceValid(
            int index)
            throws IOException {

        if (index < 0 ||
                index >= pieceCount) {

            throw new IndexOutOfBoundsException(
                    "Invalid piece index: " + index);
        }

        String actualHash =
                verifyPieceHash(index);

        String expectedHash =
                pieceHashes.get(index);

        return actualHash.equalsIgnoreCase(
                expectedHash);
    }

    /*
     * Verify all pieces in the file.
     */
    public boolean areAllPiecesValid()
            throws IOException {

        for (int i = 0;
             i < pieceCount;
             i++) {

            if (!isPieceValid(i)) {
                return false;
            }
        }

        return true;
    }

    public boolean exists() {
        return Files.isRegularFile(path);
    }

    public void validateMetadata()
            throws IOException {

        if (!exists()) {
            throw new IOException(
                    "File does not exist: " + path);
        }

        long actualSize =
                Files.size(path);

        if (actualSize != fileSize) {

            throw new IOException(
                    "File size mismatch for " +
                            fileName +
                            ": expected " +
                            fileSize +
                            ", found " +
                            actualSize);
        }

        if (pieceHashes.size() != pieceCount) {

            throw new IOException(
                    "Piece metadata mismatch for " +
                            fileName);
        }
    }

    private static int readUpTo(
            InputStream in,
            byte[] buffer)
            throws IOException {

        int total = 0;

        while (total < buffer.length) {

            int n =
                    in.read(
                            buffer,
                            total,
                            buffer.length - total);

            if (n < 0) {
                break;
            }

            if (n == 0) {
                continue;
            }

            total += n;
        }

        return total;
    }

    static MessageDigest sha256() {

        try {
            return MessageDigest.getInstance(
                    "SHA-256");

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(e);
        }
    }

    static byte[] sha256Bytes(
            byte[] data,
            int off,
            int len) {

        MessageDigest md =
                sha256();

        md.update(
                data,
                off,
                len);

        return md.digest();
    }

    static String hex(byte[] bytes) {

        StringBuilder sb =
                new StringBuilder(
                        bytes.length * 2);

        for (byte b : bytes) {

            sb.append(
                    String.format(
                            "%02x",
                            b));
        }

        return sb.toString();
    }
}