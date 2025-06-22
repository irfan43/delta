package com.irfanAK.delta;

import com.sun.tools.javac.Main;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;


public class Delta {

    private static final String APP_NAME = System.getProperty("app.name", "Unknown App");
    private static final String APP_VERSION = System.getProperty("app.version", "0.0.0");
    public static final String NAME = "${project.name}";
    public static final String VERSION = "${project.version}";


    // Method to get the version for use elsewhere
    // Method to get the version for use elsewhere
    public static String getAppVersion() {
        return APP_VERSION;
    }

    // Method to get the name for use elsewhere
    public static String getAppName() {
        return APP_NAME;
    }
    public static void main(String[] args) {
        System.out.println(APP_NAME + " V" + APP_VERSION);
        DeltaHash dh = new DeltaHash("SHA-256");
        if(args.length < 2){
            System.out.println("need more then one argument \n" +
                    "H - basic hash \n" +
                    "D - dump\n");

            System.out.println("got only " + args.length + " arguments \n");
            for (String s :
                    args) {
                System.out.println(" : " + s);
            }
            return;
        }

        try {

            if(args[0].toUpperCase().startsWith("H")){
                byte[] hash = dh.DeltaFileHash(Path.of(args[1]), 50, 10);
                System.out.println("HASH:- " +  DeltaUtil.ByteToHexString(hash) );
            }else if(args[0].toUpperCase().startsWith("D")){
                if(args.length < 3){
                    System.out.println("needs 3 arguments \n" +
                            "dump <dump file> hashfile1 hashfile2...\n");
                    return;
                }
                for (int i = 2; i < args.length; i++) {
                    System.out.println("HASHING " + args[i]);
                    dh.DeltaFileHash(Path.of(args[i]), 50, 10);
                }
                System.out.println(" saving ");
                dh.DumpToFileSorted(Path.of(args[1] + ".dat"), Path.of(args[1] + "SH.dat"), 1024*1024*10);
            }else{
                System.out.println("Unknown argument " + args[0]);
            }

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