package com.irfanAK.delta;

import java.util.ArrayList;

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
}
