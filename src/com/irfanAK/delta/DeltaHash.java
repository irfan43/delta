package com.irfanAK.delta;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


public class DeltaHash {

    // String is Chunk Size Chunk Count : Canonical Path "##:##:<Canonical Path>"
    private final HashMap<String,byte[]> FilesHashed;
    private final String Algorithm;
    private final ExecutorService executionPool;
    public final boolean MultithreadingEnable = false;

    public DeltaHash(String algorithm){
        FilesHashed = new HashMap<>();
        Algorithm = algorithm;
        executionPool = Executors.newCachedThreadPool();
    }

    /**
     * Hashes the given file using the given @param ChunkCount plus two (the starting and ending chunks)
     * This uses the hashmap cache of files that have been has and will return the stored hash
     * if file is already been hashed before with this instance, or by any loaded hash-table files
     * Folders will be hashed and return similar
     * @param path location of the file or folder
     * @param chunkSize Size of the chunks in kilobytes if chunkCount is -1 will consider full hash and ignore chunking, chunkSize will be the buffer size
     * @param chunkCount plus two (the starting and ending chunks) if chunkCount is -1 will consider full hash and ignore chunking
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
            throw new FileNotFoundException(" no file at " + path.toFile().getCanonicalPath());

        String canonicalPath = path.toFile().getCanonicalPath();
        String key = chunkSize + ":" + chunkCount + ":" + canonicalPath;

        boolean isFile =      Files.isRegularFile(path);  // Check if it's a regular file
        boolean isDirectory = Files.isDirectory(path);    // Check if it's a directory
        if(!isDirectory && !isFile)
            throw new IllegalArgumentException("File is not regular file, can not be hashed.");

        if(isFile)
            if ( (Files.size(path) / 2) < (long) chunkCount * (long) chunkSize * 1024L)
                chunkCount = -1;

        if(chunkCount == -1)
            key = "FULL:0:" + canonicalPath;

        if(FilesHashed.containsKey(key))
            return FilesHashed.get(key);

        // If cache misses we will try to hash the file and store in hashmap
        byte[] hash;
        if(isFile){
            //System.out.println("Hashing " + key);
            hash = FileChunkHash(path, chunkSize, chunkCount, Algorithm);
            FilesHashed.put(key,hash);
        }else{
            hash = FolderHash(path, chunkSize, chunkCount);
            FilesHashed.put(key,hash);
        }
        return hash;
    }

    private byte[] FolderHash(Path path, int chunkSize, int chunkCount) throws NoSuchAlgorithmException, IOException {
        List<Path> filepath = Files.walk(path,1).filter(f -> Files.isRegularFile(f) || Files.isDirectory(f)).collect(Collectors.toList());

        ArrayList<DeltaHashingThread> threadList = new ArrayList<>();
        ArrayList<byte[]> hashes = new ArrayList<>();
        for (Path p : filepath) {
            if(p.compareTo(path) == 0)
                continue;
            if(!Files.isRegularFile(p) && !Files.isDirectory(p) || p.toFile().getCanonicalPath().contains("$RECYCLE.BIN") || p.toFile().getCanonicalPath().contains("System Volume Information"))
                continue;

            if(MultithreadingEnable){
                DeltaHashingThread t = new DeltaHashingThread(p,this,chunkSize,chunkCount);
                threadList.add(t);
                executionPool.execute(t);
            }else {
                byte[] fileHash = DeltaFileHash(p, chunkSize, chunkCount);
                hashes.add(fileHash);
            }
        }
        if(MultithreadingEnable){
            try {
                boolean finished = false;
                while (!finished) {
                    finished = true;
                    for (DeltaHashingThread t : threadList) {
                        if (t.isCompleted()) {
                            finished = false;
                            break;
                        }
                    }
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                throw new IOException("ILLEGAL INTERRUPTION");
            }
            for (DeltaHashingThread t : threadList) {
                hashes.add(t.getHashOutput());
            }
        }
        return FolderArrayHash(hashes, Algorithm);
    }

    private static byte[] FolderArrayHash(ArrayList<byte[]> hashes, String algorithm) throws NoSuchAlgorithmException {
        hashes = DeltaUtil.HashesSort(hashes);
        MessageDigest md = MessageDigest.getInstance(algorithm);
        for (byte[] hash : hashes)
            md.update(hash);
        return md.digest();
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
        byte[] hash;

        try(SeekableByteChannel sbc = FileChannel.open(path, StandardOpenOption.READ)){
            MessageDigest md = MessageDigest.getInstance(algorithm);
            long size,deltaPosition = 0;
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

    public void DumpToFile(Path path) throws IOException {
        BufferedWriter bw = Files.newBufferedWriter(path);
        for(String key : FilesHashed.keySet()){
            byte[] hash = FilesHashed.get(key);
            String hexHash = DeltaUtil.ByteToHexString(hash) + ":" + key ;
            bw.write(hexHash);
            bw.newLine();
            bw.flush();
        }
        bw.close();
    }

    public void DumpToFileSorted(Path path, Path duplicated, long min_size) throws IOException {
        BufferedWriter bw = Files.newBufferedWriter(path);
        BufferedWriter bwd = Files.newBufferedWriter(duplicated);
        ArrayList<String> hashes = new ArrayList<>();
        for(String key : FilesHashed.keySet()){
            byte[] hash = FilesHashed.get(key);
            String hexHash = DeltaUtil.ByteToHexString(hash) + ":" + key ;
            hashes.add(hexHash);
        }
        Collections.sort(hashes);
        for (String hash : hashes) {
            bw.write(hash);
            bw.newLine();
        }
        int number_of_duplicates = 0;
        long size_saved = 0;

        ArrayList<String> duplicates = new ArrayList<>();
        for (int i = 1; i < hashes.size(); i++) {
            if(GetHash(hashes.get(i)).equals(GetHash(hashes.get(i - 1)))){
                long file_size = Files.size(Path.of(GetPaths(hashes.get(i - 1))));
                boolean isdir = Files.isDirectory(Path.of(GetPaths(hashes.get(i - 1))));
                if(file_size < min_size &&  !isdir )
                    continue;
                String file_size_string = "DIR";
                if(!isdir)
                    file_size_string = padLeftZeros(file_size + "", 15) + "#" + DeltaUtil.GetHumanReadableSize(file_size);

                while(GetHash(hashes.get(i)).equals(GetHash(hashes.get(i - 1)))){
//                    bwd.write(hashes.get(i - 1));
//                    bwd.newLine();
                    duplicates.add(file_size_string + " " + GetPaths(hashes.get(i - 1)));
                    i++;
                    size_saved += file_size;
                    number_of_duplicates++;
                }
//                bwd.write(hashes.get(i - 1));
//                bwd.newLine();
//                bwd.newLine();
                duplicates.add(file_size_string + " " + GetPaths(hashes.get(i - 1)));
                number_of_duplicates++;
            }
        }
        Collections.sort(duplicates);
        for (String l : duplicates) {
            bwd.write(RemoveFileSize(l));
            bwd.newLine();
        }


        //System.out.println(" Duplicate files found " + number_of_duplicates + " using " + DeltaUtil.GetHumanReadableSize(size_saved) + " extra data");
        bw.flush();
        bw.close();
        bwd.flush();
        bwd.close();
    }

    private String RemoveFileSize(String s){
        int n = s.indexOf('#');
        return s.substring(n + 1);
    }

    private String padLeftZeros(String s, int n) {
        StringBuilder sb = new StringBuilder();
        sb.append("0".repeat(Math.max(0, n)));
        return sb.substring(s.length()) + s;
    }

    private String GetPaths(String hashkey) {
        String path = hashkey.substring(65);
        for (int i = 0; i < 2; i++) {
            int n = path.indexOf(':');
            path = path.substring(n + 1);
        }
        return path;
    }

    public static String GetHash(String hashkey){
        return hashkey.substring(0,64);
    }
}
