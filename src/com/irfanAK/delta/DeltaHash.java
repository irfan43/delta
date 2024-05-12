package com.irfanAK.delta;

import com.sun.source.doctree.SeeTree;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeltaHash {

    // String is Chunk Size Chunk Count : Canonical Path "##:##:<Canonical Path>"
    private HashMap<String,byte[]> FilesHashed;
    private final String Algorithm;

    public DeltaHash(String algorithm){
        FilesHashed = new HashMap<>();
        Algorithm = algorithm;
    }

    /**
     * Hashes the given file using the given @param ChunkCount plus two (the starting and ending chunks)
     * This uses the hashmap cache of files that have been has and will return the stored hash
     * if file is already been hashed before with this instance, or by any loaded hash-table files
     * Folders will be hashed and return similar
     * @param path location of the file or folder
     * @param chunkSize Size of the chunks in kilobytes if chunkCount is -1 will consider full hash and ignore chunking, chunkSize will be the buffer size
     * @param chunkCount plus two (the starting and ending chunks)
     * @return byte hash of the file or folder
     */
    public byte[] DeltaFileHash(Path path, int chunkSize, int chunkCount) throws IOException, NoSuchAlgorithmException {
        // Sanity check of the given file
        if(path == null)
            throw new NullPointerException();
        if(chunkCount < 0 && chunkCount != -1)
            throw new IllegalArgumentException("Chunk Count must be a positive number or -1. chunkCount can not be " + chunkCount + ".");
        if(chunkSize < 0)
            throw new IllegalArgumentException("Chunk Size must be a positive number. chunkSize can not be " + chunkCount + ".");
        if(!Files.exists(path))
            throw new FileNotFoundException();

        String canonicalPath = path.toFile().getCanonicalPath();
        String key = chunkSize + ":" + chunkCount + ":" + canonicalPath;

        boolean isFile =      Files.isRegularFile(path);  // Check if it's a regular file
        boolean isDirectory = Files.isDirectory(path);    // Check if it's a directory
        if(!isDirectory && !isFile)
            throw new IllegalArgumentException("File is not regular file, can not be hashed.");

        if(isFile)
            if ( (Files.size(path) / 2) < (long) chunkCount * (long) chunkSize * 1024L)
                chunkSize = -1;

        if(chunkSize == -1)
            key = "FULL:" + canonicalPath;

        if(FilesHashed.containsKey(key))
            return FilesHashed.get(key);

        // If cache misses we will try to hash the file and store in hashmap
        byte[] hash = null;
        if(isFile){
            hash = FileChunkHash(path, chunkSize, chunkCount, Algorithm);
            FilesHashed.put(key,hash);
        }else{
            List<Path> filepath = Files.walk(path,1).filter(f -> Files.isRegularFile(f) || Files.isDirectory(f)).collect(Collectors.toList());
            for (Path p : filepath) {
                System.out.println(p.toFile().getCanonicalPath());
            }
        }
        return hash;
    }

    /**
     * Hashes the given file
     * @param path to the file to be hash
     * @param chunkSize the number of chunks in Kilobytes if chunk size is -1 will consider full hash and ignore chunking
     * @param chunkCount the amount of chunks plus 2 to be used to divide the file into example: if chunk size is given as 4 the code will use 4 + 2 = 6 chunks
     * @return byte hash of the given file using the chunks and chunk count
     */
    private static byte[] FileChunkHash(Path path, int chunkSize, int chunkCount, String algorithm) throws IOException, NoSuchAlgorithmException {
        if(path == null)
            throw new NullPointerException();
        if(chunkCount < 0 && chunkCount != -1)
            throw new IllegalArgumentException("Chunk Count must be a positive number or -1. chunkCount can not be " + chunkCount + ".");
        if(chunkSize < 0)
            throw new IllegalArgumentException("Chunk Size must be a positive number. chunkSize can not be " + chunkCount + ".");
        if(!Files.exists(path))
            throw new FileNotFoundException();

        boolean fullHash = chunkCount == -1;
        byte[] hash = null;

        try(SeekableByteChannel sbc = FileChannel.open(path, StandardOpenOption.READ)){
            MessageDigest md = MessageDigest.getInstance(algorithm);
            long size = 0,deltaPosition = 0;
            int bbSize = 1024 * chunkSize;

            if(!fullHash) {
                size = sbc.size();
                chunkCount += 2;
                deltaPosition = (size - (long) chunkCount * bbSize) / (chunkCount - 1);
            }
            for (int i = 0; i < chunkCount || fullHash; i++) {
                ByteBuffer bb = ByteBuffer.allocate(bbSize);
                int readSize = sbc.read(bb);
                if(readSize <= 0)
                    break;
                if(readSize == bbSize)
                    md.update(bb.array());
                else
                    md.update(Arrays.copyOf(bb.array(), readSize));


                if(!fullHash)
                    sbc.position(deltaPosition + sbc.position());
            }
            hash = md.digest();
        }
        return hash;
    }


}
