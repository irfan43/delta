package com.irfanAK.delta;

import java.io.BufferedReader;
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
import java.util.stream.Collectors;


public class DeltaHash {

    // String is Chunk Size Chunk Count : Canonical Path "##:##:<Canonical Path>"
    private final HashMap<String,byte[]> FilesHashed;
    private final String Algorithm;
    // Threshold before which partial hashing is ignored and will do a full hash to save time
    // for example if a partial hash of a file is hashing 5mb and the file is 7mb,
    //          it may be better to just do a full hash
    // this value is kept as percentage
    private final int FullHashingThreshold;

    public DeltaHash(String algorithm){
        FilesHashed = new HashMap<>();
        Algorithm = algorithm;
        FullHashingThreshold = 50;
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
        System.out.println("Working on " + path);
        boolean isFile =      Files.isRegularFile(path);  // Check if it's a regular file
        boolean isDirectory = Files.isDirectory(path);    // Check if it's a directory
        if(!isDirectory && !isFile)
            throw new IllegalArgumentException("File is not regular file, can not be hashed.");

        if(isFile)
            if ( ( (Files.size(path)/100)  * FullHashingThreshold) < (long) chunkCount * (long) chunkSize * 1024L)
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
            if(hash != null)
                FilesHashed.put(key,hash);
            else{
                hash = new byte[32];
                Arrays.fill(hash, (byte)0);
            }

        }
        return hash;
    }

    private byte[] FolderHash(Path path, int chunkSize, int chunkCount) throws NoSuchAlgorithmException, IOException {
        List<Path> filepath = Files.walk(path,1).filter(f -> Files.isRegularFile(f) || Files.isDirectory(f)).collect(Collectors.toList());
        filepath.remove(path);
        if(filepath.size() == 0){
            return null;
        }
        ArrayList<DeltaHashingThread> threadList = new ArrayList<>();
        ArrayList<byte[]> hashes = new ArrayList<>();
        for (Path p : filepath) {
            if(p.compareTo(path) == 0)
                continue;
            if(!Files.isRegularFile(p) && !Files.isDirectory(p) || p.toFile().getCanonicalPath().contains("$RECYCLE.BIN") || p.toFile().getCanonicalPath().contains("System Volume Information"))
                continue;


            byte[] fileHash = DeltaFileHash(p, chunkSize, chunkCount);
            hashes.add(fileHash);

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

    public void LoadFromFile(Path path) throws IOException {
        BufferedReader br = Files.newBufferedReader(path);
        String line;
        do{
            line = br.readLine();
            if(line != null){
                int n = line.indexOf(":");

            }
        }while(line != null);
        br.close();
    }

    public void DumpToFileSorted(Path dataFilePath, Path duplicated, long min_size) throws IOException {
        BufferedWriter bw = Files.newBufferedWriter(dataFilePath);
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

        ArrayList<String> duplicateParentFolders = new ArrayList<>();
        ArrayList<String> duplicateFolders = new ArrayList<>();
        ArrayList<DeltaDuplicateSet> duplicateSets = new ArrayList<>();
        for (int i = 1; i < hashes.size(); i++) {
            if(GetHash(hashes.get(i)).equals(GetHash(hashes.get(i - 1)))){
                String hash = GetHash(hashes.get(i));

                long file_size = Files.size(Path.of(GetPaths(hashes.get(i - 1))));
                boolean isDir = Files.isDirectory(Path.of(GetPaths(hashes.get(i - 1))));
                if(file_size < min_size &&  !isDir )
                    continue;
                String file_size_string = "DIR";
                if(!isDir)
                    file_size_string = padLeftZeros(file_size + "", 15) + "#" + DeltaUtil.GetHumanReadableSize(file_size);
                file_size_string += " " + hash.substring(0,6);

                DeltaDuplicateSet dds = new DeltaDuplicateSet(file_size_string + " " + hash);
                while((i < hashes.size()) && GetHash(hashes.get(i)).equals(GetHash(hashes.get(i - 1)))){
                    duplicates.add(file_size_string + " " + GetPaths(hashes.get(i - 1)));
                    dds.addHash(file_size_string + " " + GetPaths(hashes.get(i - 1)));
                    if(isDir)
                        duplicateFolders.add(GetPaths(hashes.get(i - 1)));
                    duplicateParentFolders.add(GetParentPath(hashes.get(i - 1)));

                    i++;
                    size_saved += file_size;
                    number_of_duplicates++;
                }

                dds.addHash(file_size_string + " " + GetPaths(hashes.get(i - 1)));
                duplicates.add(file_size_string + " " + GetPaths(hashes.get(i - 1)));
                duplicateSets.add(dds);
                number_of_duplicates++;
            }
        }
//        for(DeltaDuplicateSet dds : duplicateSets){
//            ArrayList<String> paths = dds.getFile_Hashes();
//            boolean isInDuplicateFolder = true;
//            for(String p : paths){
//                p = GetPaths(p);
//                if(!duplicateFolders.contains(p)){
//                    isInDuplicateFolder = false;
//                }
//            }
//            if(isInDuplicateFolder)
//                duplicateSets.remove(dds);
//        }


//        Collections.sort(duplicates);
//        for (String l : duplicates) {
//            bwd.write(RemoveFileSize(l));
//            bwd.newLine();
//        }
        Collections.sort(duplicateSets);
        BufferedWriter batADbw = Files.newBufferedWriter(Path.of("AutoDelete.bat"));
        for(DeltaDuplicateSet dds : duplicateSets){
            boolean isDir = false;
            ArrayList<String> ddsHashes = dds.getFile_Hashes();
            ArrayList<String> pathDelBat = new ArrayList<>();

            if(ddsHashes.size() <= 1)
                continue;

            for(String h : ddsHashes){
                h = RemoveFileSize(h);
                isDir = h.substring(0,3).equalsIgnoreCase("DIR");
                bwd.write(h);
                bwd.newLine();
                h = h.substring(h.indexOf(" ")+ 1);
                h = h.substring(h.indexOf(" ") + 1);

                pathDelBat.add(h);
            }
            bwd.newLine();

            String cmd = isDir ? "RMDIR" : "DEL";
            String cmdEnd = isDir ? " /s /q" : "";
            String org = pathDelBat.remove(0);
            for(String s : pathDelBat){
                bwd.write("ECHO orginal is located at " + org + " > \"" + s + "\".txt\n" );
                bwd.write(cmd + " \"" + s + "\"" + cmdEnd + "\n");


                batADbw.write("ECHO orginal is located at " + org + " > \"" + s + "\".txt\n" );
                batADbw.write(cmd + " \"" + s + "\"" + cmdEnd + "\n");
            }
            bwd.newLine();
            bwd.newLine();

        }


        batADbw.flush();
        batADbw.close();


        System.out.println(" Duplicate files found " + number_of_duplicates + " using " + DeltaUtil.GetHumanReadableSize(size_saved) + " extra data");
        bw.flush();
        bw.close();
        bwd.flush();
        bwd.close();
    }

    public String FolderRelation(String PathA,String PathB){

        /*
         * - UNRELATED
         * --  if both directories only have one file in common
         *
         * - MULTIPLE MATCHING
         * -- having multiple files matching over the directories but each directory has other non related files
         *
         * - SubSet A to B
         * -- B has all the files of A
         *
         * - SubSet B to A
         * -- A has all the files of B
         *
         * - folders matches
         */
        //boolean isFileA =      Files.isRegularFile(Path.of(PathA));
        boolean isDirA =       Files.isDirectory(Path.of(PathA));
        //boolean isFileB =      Files.isRegularFile(Path.of(PathB));
        boolean isDirB =       Files.isDirectory(Path.of(PathB));

        if(! (isDirA && isDirB) ){
            return "FF"; //files
        }

        return "UR";  // unrelated
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

    private String GetPaths(String hashKey) {
        String path = hashKey.substring(65);
        for (int i = 0; i < 2; i++) {
            int n = path.indexOf(':');
            path = path.substring(n + 1);
        }
        return path;
    }

    private String GetParentPath(String hashKey) {
        String path = GetPaths(hashKey);
        int n = path.lastIndexOf("\\");
        return path.substring(0,n);
    }

    public static String GetHash(String hashKey){
        return hashKey.substring(0,64);
    }
}
