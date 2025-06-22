package com.irfanAK.delta;

import java.util.ArrayList;

public class DeltaDuplicateSet  implements Comparable<DeltaDuplicateSet>{

    private String Hash;
    private ArrayList<String> File_Hashes ;

    public DeltaDuplicateSet(String hash){
        Hash = hash;
        File_Hashes = new ArrayList<>();
    }

    public void addHash(String hash){
        File_Hashes.add(hash);
    }

    public String getHash(){
        return Hash;
    }

    public ArrayList<String> getFile_Hashes(){
        return File_Hashes;
    }

    @Override
    public int compareTo(DeltaDuplicateSet deltaDuplicateSet){
        return Hash.compareTo( deltaDuplicateSet.getHash() );
    }
}
