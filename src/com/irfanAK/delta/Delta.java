package com.irfanAK.delta;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Delta {


    public static void main(String[] args) {
        System.out.println("Delta V1.00");
        DeltaHash dh = new DeltaHash("SHA-256");
        try {
            dh.DeltaFileHash(Path.of("C:\\Folder1"), 5, 2);
            dh.DeltaFileHash(Path.of("C:\\Folder2"), 5, 2);

            dh.DumpToFileSorted(Path.of("DE.dat"), Path.of("DESH.dat"), 1024L*1024L);
        } catch (IOException e) {
            System.out.println("Ran into IO Exception");
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static byte[] hash(Path fileToHash){
       byte[] hash = new byte[0];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[2048];

            BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(fileToHash));
            while (true) {
                int resp = bis.read(buffer);
                if(resp == -1)
                    break;
                md.update(buffer,0,resp);
            }
            hash = md.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return hash;
    }

    public static byte[] hash2(Path fileToHash){
        byte[] hash = new byte[0];
        try {
            byte[] data = Files.readAllBytes(fileToHash);
            hash = MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return hash;
    }

    public static String ByteToHexString(byte[] byteArray)
    {
        StringBuilder hex = new StringBuilder();
        for (byte i : byteArray)
            hex.append(String.format("%02X", i));
        return hex.toString();
    }
}