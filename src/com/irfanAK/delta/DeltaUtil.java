package com.irfanAK.delta;

import java.util.ArrayList;
import java.util.Map;

public class DeltaUtil {

    public static ArrayList<byte[]> HashesSort(ArrayList<byte[]> input){
        if(input.size() < 1)
            return input;

        ArrayList<byte[]> left = new ArrayList<>();
        ArrayList<byte[]> right = new ArrayList<>();
        byte[] pivot = input.remove(0);

        while (input.size() > 0){
            byte[] ele = input.remove(0);
            if(compareByteArray(pivot,ele) > 0){
                left.add(ele);
            }else {
                right.add(ele);
            }
        }

        left = HashesSort(left);
        right = HashesSort(right);

        ArrayList<byte[]> sortedList = new ArrayList<>(left);
        sortedList.add(pivot);
        sortedList.addAll(right);
        return sortedList;
    }

    private static int compareByteArray(byte[] a, byte[] b){
        if(a.length != b.length)
            throw new IllegalArgumentException("Unmatched Length of Arrays");

        for (int i = 0; i < a.length; i++) {
            if(a[i] == b[i])
                continue;
            if(a[i] > b[i])
                return 1;
            if(b[i] > a[i])
                return -1;
        }

        return 0;
    }

    public static String ByteToHexString(byte[] byteArray)
    {
        StringBuilder hex = new StringBuilder();
        for (byte i : byteArray)
            hex.append(String.format("%02X", i));
        return hex.toString();
    }

    public static String GetHumanReadableSize(long size) {
        String[] sizeUnits = new String[]{"bytes", "kb", "mb", "gb", "tb", "pb"};
        int unitIndex;
        int decimal = 0;
        for (unitIndex = 0; unitIndex < (sizeUnits.length - 1); unitIndex++) {
            if(size < 1024)
                break;
            decimal = (int) (size%1024);
            size = size/1024;

        }
        return size + "." + decimal + sizeUnits[unitIndex];
    }
}
