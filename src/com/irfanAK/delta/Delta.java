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
    /**
    *
    * */
    public static void main(String[] args) {
        System.out.println("Delta V1.00");
        DeltaHash dh = new DeltaHash("SHA-256");
        try {
            byte[] hex = dh.DeltaFileHash(Path.of("CDs"), 10, -1);
            //byte[] hex2 = dh.DeltaFileHash(Path.of("test2.mkv"), 10, 2);
            //System.out.println(ByteToHexString(hex));
            System.out.println(ByteToHexString(hex));
        } catch (IOException e) {
            System.out.println("Ran into IO Exception");

            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //System.out.println(Files.isDirectory(Path.of("testing_data")));
        //System.out.println( convertByteToHexadecimal(hash(Path.of("test.mkv"))) );
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