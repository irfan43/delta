package com.irfanAK.delta;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

public class DeltaHashingThread implements Runnable{

    public byte[] hashOutput = null;
    private boolean excpThrown = false;
    private boolean completed = false;
    private final DeltaHash dh;
    private final Path path;
    private final int chunkSize;
    private final int chunkCount;

    public DeltaHashingThread(Path path, DeltaHash dh, int chunkSize, int chunkCount){
        this.dh = dh;
        this.path = path;
        this.chunkSize = chunkSize;
        this.chunkCount = chunkCount;
    }

    @Override
    public void run() {
        try {
            hashOutput = dh.DeltaFileHash(path,chunkSize,chunkCount);
            completed = true;
        } catch (IOException | NoSuchAlgorithmException e) {
            excpThrown = true;
            e.printStackTrace();
        }
    }

    public boolean isCompleted(){
        return completed;
    }

    public byte[] getHashOutput() throws IOException {
        if(excpThrown){
            throw new IOException();
        }
        return hashOutput;
    }
}
